package com.beertracker.domain

/**
 * One beer from the read-only Systembolaget catalog snapshot. This is
 * reference data: saving a beer copies the fields the user cares about onto
 * the TriedBeer, so catalog refreshes never change anything the user saved.
 */
data class CatalogProduct(
    val articleNumber: String,
    val articleNumberShort: String?,
    val name: String,
    val brewery: String,
    val type: String,
    val alcoholPercent: Double?,
    val volumeMl: Int?,
    val price: Double?,
    val country: String?,
    val imageUrl: String?,
    /**
     * Systembolaget's food pairing symbols for this product, already mapped
     * to [Pairing] labels and ordered by the vocabulary. Empty when the
     * product has none, which is common.
     */
    val pairings: List<String> = emptyList(),
) {
    /**
     * The catalog stores the CDN base URL without an extension. Appending
     * _400.jpg yields a 400 pixel JPEG, verified working in August 2026, for
     * example https://product-cdn.systembolaget.se/productimages/50786609/50786609_400.jpg
     */
    val displayImageUrl: String?
        get() = imageUrl?.let { base -> base + "_400.jpg" }
}
