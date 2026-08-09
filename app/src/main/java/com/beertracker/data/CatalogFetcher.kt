package com.beertracker.data

import com.beertracker.domain.CatalogProduct
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The public product-search key from Systembolaget's own website JavaScript.
 * Every visitor's browser sends it in the open with each search request, so
 * shipping it inside the APK discloses nothing that is not already public.
 * It is deliberately a plain constant, not a secret.
 */
internal const val SYSTEMBOLAGET_SUBSCRIPTION_KEY = "cfc702aed3094c86b92d6d4ff7a54c84"

private const val SEARCH_URL =
    "https://api-extern.systembolaget.se/sb-api-ecommerce/v1/productsearch/search"
private const val PAGE_SIZE = 30

// categoryLevel1=Öl with the Ö percent-encoded, matching scripts/fetch_catalog.py.
private const val CATEGORY_FILTER = "categoryLevel1=%C3%96l"
private const val MAX_PAGES = 500
private const val MAX_RETRIES = 5

// Two full sweeps with different deterministic sort keys, unioned by
// articleNumber. Necessary because the live search API has no stable sort
// for items that tie on the sort field: their relative order can still
// drift between page requests, so a single sweep silently drops a
// meaningful slice of a category this large (measured 2026-08-09: a lone
// Name-sorted sweep recovered only 4826 of 4984 Öl products). A second
// sweep on an unrelated field ties differently and recovers nearly all the
// rest (4966 of 4984 unioned). Matches scripts/fetch_catalog.py.
private val SORT_SWEEPS = listOf("Name" to "Ascending", "Price" to "Ascending")

interface CatalogFetcher {
    /**
     * Fetches every beer in the live assortment, or throws. Runs one full
     * sweep per entry in SORT_SWEEPS, unioning by article number.
     */
    suspend fun fetchAllBeers(): List<CatalogProduct>
}

class SystembolagetCatalogFetcher(
    private val httpGet: (String) -> String = ::defaultHttpGet,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pageDelayMillis: Long = 300L,
    private val retryDelayMillis: Long = 1_000L,
) : CatalogFetcher {

    override suspend fun fetchAllBeers(): List<CatalogProduct> = withContext(ioDispatcher) {
        val beers = mutableListOf<CatalogProduct>()
        for ((sortBy, sortDirection) in SORT_SWEEPS) {
            fetchSweep(sortBy, sortDirection).filter(::isBeer).mapTo(beers, ::mapProduct)
        }
        beers
            .filter { it.articleNumber.isNotEmpty() }
            .distinctBy { it.articleNumber }
            .sortedBy { it.articleNumber }
    }

    private suspend fun fetchSweep(sortBy: String, sortDirection: String): List<JSONObject> {
        val products = mutableListOf<JSONObject>()
        var page = 1
        repeat(MAX_PAGES) {
            val pageJson = fetchPageWithRetry(pageUrl(page, sortBy, sortDirection))
            products.addAll(parseProductsPage(pageJson))
            val nextPage = parseNextPage(pageJson)
            if (nextPage == null || nextPage <= 0) return products
            page = nextPage
            delay(pageDelayMillis)
        }
        return products
    }

    // A handful of the ~170 sequential requests in a full sweep hit
    // transient network errors in practice; retry with backoff instead of
    // aborting the whole sweep.
    private suspend fun fetchPageWithRetry(url: String): String {
        var lastError: IOException? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return httpGet(url)
            } catch (error: IOException) {
                lastError = error
                delay(retryDelayMillis * (attempt + 1))
            }
        }
        throw lastError!!
    }
}

internal fun pageUrl(page: Int, sortBy: String, sortDirection: String): String =
    "$SEARCH_URL?size=$PAGE_SIZE&page=$page&$CATEGORY_FILTER&sortBy=$sortBy&sortDirection=$sortDirection"

internal fun parseProductsPage(pageJson: String): List<JSONObject> {
    val products = JSONObject(pageJson).optJSONArray("products") ?: return emptyList()
    return (0 until products.length()).mapNotNull { products.optJSONObject(it) }
}

// NOTE: the API signals the end of results via metadata.nextPage == -1, not
// an empty products array -- an out-of-range page 404s instead of
// returning {"products": []}.
internal fun parseNextPage(pageJson: String): Int? {
    val metadata = JSONObject(pageJson).optJSONObject("metadata") ?: return null
    return if (metadata.has("nextPage")) metadata.optInt("nextPage") else null
}

internal fun isBeer(product: JSONObject): Boolean =
    product.optString("categoryLevel1") == "Öl"

/**
 * Maps one raw API product to the catalog model. Must stay field for field
 * identical to map_product in scripts/fetch_catalog.py; both test suites use
 * the same sample product to hold the two mappers together.
 */
internal fun mapProduct(product: JSONObject): CatalogProduct {
    val nameBold = product.optStringOrNull("productNameBold")?.trim().orEmpty()
    val nameThin = product.optStringOrNull("productNameThin")?.trim().orEmpty()
    val imageUrl = product.optJSONArray("images")
        ?.optJSONObject(0)
        ?.optStringOrNull("imageUrl")
    return CatalogProduct(
        articleNumber = product.optStringOrNull("productNumber").orEmpty(),
        articleNumberShort = product.optStringOrNull("productNumberShort"),
        name = listOf(nameBold, nameThin).filter { it.isNotEmpty() }.joinToString(" "),
        brewery = product.optStringOrNull("producerName").orEmpty(),
        type = product.optStringOrNull("categoryLevel2")
            ?: product.optStringOrNull("categoryLevel3")
            ?: "Öl",
        alcoholPercent = product.optDoubleOrNull("alcoholPercentage"),
        // Half-to-even, matching Python's round(): int(round(330.5)) == 330.
        volumeMl = product.optDoubleOrNull("volume")?.let { Math.rint(it).toInt() },
        price = product.optDoubleOrNull("price"),
        // Field-specific, not optStringOrNull: the seed script's country field
        // is a plain passthrough (no "or" fallback), so a present empty string
        // must stay "" instead of collapsing to null like the other fields do.
        country = if (product.isNull("country")) null else product.getString("country"),
        imageUrl = imageUrl,
    )
}

private fun defaultHttpGet(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("ocp-apim-subscription-key", SYSTEMBOLAGET_SUBSCRIPTION_KEY)
        connection.setRequestProperty("Referer", "https://www.systembolaget.se/")
        connection.setRequestProperty("Accept", "application/json")
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            throw IOException("Systembolaget product search returned HTTP $code")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
