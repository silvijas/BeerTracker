package com.beertracker.data

import com.beertracker.domain.CatalogProduct
import org.json.JSONObject

data class CatalogSeed(
    val snapshotVersion: String,
    val beers: List<CatalogProduct>,
)

/** Parses the bundled assets/catalog/beers.json written by scripts/fetch_catalog.py. */
fun parseCatalogAsset(text: String): CatalogSeed {
    val root = JSONObject(text)
    val beersJson = root.getJSONArray("beers")
    val beers = ArrayList<CatalogProduct>(beersJson.length())
    for (index in 0 until beersJson.length()) {
        beers.add(beersJson.getJSONObject(index).toCatalogProduct())
    }
    return CatalogSeed(
        snapshotVersion = root.getString("snapshotVersion"),
        beers = beers,
    )
}

private fun JSONObject.toCatalogProduct() = CatalogProduct(
    articleNumber = getString("articleNumber"),
    articleNumberShort = optStringOrNull("articleNumberShort"),
    name = optString("name"),
    brewery = optString("brewery"),
    type = optString("type"),
    alcoholPercent = optDoubleOrNull("alcoholPercent"),
    volumeMl = optDoubleOrNull("volumeMl")?.toInt(),
    price = optDoubleOrNull("price"),
    country = optStringOrNull("country"),
    imageUrl = optStringOrNull("imageUrl"),
    pairings = optStringList("pairings"),
)

/** Absent keys, JSON nulls, and empty strings all become Kotlin null. */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

/** Absent keys and JSON nulls become Kotlin null instead of NaN. */
internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

/**
 * Absent keys, JSON nulls, and empty arrays all become an empty list. Older
 * assets predate the pairings field entirely, so this must never throw.
 */
internal fun JSONObject.optStringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }
}
