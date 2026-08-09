package com.beertracker.domain

import java.text.Collator
import java.util.Locale

/**
 * Pure filtering and sorting for the catalog browser and the add-view
 * suggestions. Matching runs in Kotlin instead of SQL because SQLite only
 * case-folds ASCII, which would make Swedish letters match inconsistently.
 */
object CatalogBrowseLogic {

    fun filterAndSort(products: List<CatalogProduct>, query: String): List<CatalogProduct> {
        val trimmed = query.trim()
        val digitsOnly = trimmed.isNotEmpty() && trimmed.all(Char::isDigit)
        val filtered = if (trimmed.isEmpty()) {
            products
        } else {
            products.filter { product ->
                val matchesText = listOf(product.name, product.brewery, product.type)
                    .any { it.contains(trimmed, ignoreCase = true) }
                val matchesNumber = digitsOnly &&
                    (
                        product.articleNumber.startsWith(trimmed) ||
                            product.articleNumberShort?.startsWith(trimmed) == true
                        )
                matchesText || matchesNumber
            }
        }
        val collator = Collator.getInstance(Locale("sv", "SE"))
        return filtered.sortedWith(
            compareBy(collator, CatalogProduct::name).thenBy(collator, CatalogProduct::brewery),
        )
    }
}
