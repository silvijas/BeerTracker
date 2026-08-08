package com.beertracker.data

import com.beertracker.domain.CatalogProduct
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
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

interface CatalogFetcher {
    /**
     * Fetches every beer in the live assortment, or throws. Politeness rules
     * match the seed script: sequential pages of 30 with a small delay, stop
     * at the first empty page.
     */
    suspend fun fetchAllBeers(): List<CatalogProduct>
}

class SystembolagetCatalogFetcher(
    private val httpGet: (String) -> String = ::defaultHttpGet,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pageDelayMillis: Long = 300L,
) : CatalogFetcher {

    override suspend fun fetchAllBeers(): List<CatalogProduct> = withContext(ioDispatcher) {
        val beers = mutableListOf<CatalogProduct>()
        for (page in 1..MAX_PAGES) {
            val products = parseProductsPage(httpGet(pageUrl(page)))
            if (products.isEmpty()) break
            products.filter(::isBeer).mapTo(beers, ::mapProduct)
            delay(pageDelayMillis)
        }
        beers
            .filter { it.articleNumber.isNotEmpty() }
            .distinctBy { it.articleNumber }
            .sortedBy { it.articleNumber }
    }
}

internal fun pageUrl(page: Int): String =
    "$SEARCH_URL?size=$PAGE_SIZE&page=$page&$CATEGORY_FILTER"

internal fun parseProductsPage(pageJson: String): List<JSONObject> {
    val products = JSONObject(pageJson).optJSONArray("products") ?: return emptyList()
    return (0 until products.length()).mapNotNull { products.optJSONObject(it) }
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
        volumeMl = product.optDoubleOrNull("volume")?.roundToInt(),
        price = product.optDoubleOrNull("price"),
        country = product.optStringOrNull("country"),
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
