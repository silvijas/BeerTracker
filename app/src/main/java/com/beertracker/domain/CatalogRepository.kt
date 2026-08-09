package com.beertracker.domain

import kotlinx.coroutines.flow.Flow

data class CatalogStatus(
    val beerCount: Int,
    /** Null until the first successful in-app refresh; the seed does not count. */
    val lastRefreshUtc: Long?,
)

interface CatalogRepository {
    /**
     * Looks a product up by article number. The input may be raw scanner or
     * keyboard text: everything except digits is dropped, then the digits are
     * matched exactly against the full article number and the short shelf
     * number, with one retry without leading zeros.
     */
    suspend fun findByArticleNumber(raw: String): CatalogProduct?

    /** Streams the whole catalog. Room re-emits automatically after refreshes. */
    fun observeProducts(): Flow<List<CatalogProduct>>

    fun observeStatus(): Flow<CatalogStatus?>
}
