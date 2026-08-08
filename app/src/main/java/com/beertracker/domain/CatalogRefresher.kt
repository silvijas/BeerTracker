package com.beertracker.domain

sealed interface RefreshResult {
    data class Success(val beerCount: Int, val refreshedUtc: Long) : RefreshResult
    data class Failure(val reason: String) : RefreshResult
}

interface CatalogRefresher {
    /**
     * Replaces the catalog with the live Systembolaget assortment. The whole
     * fetch must succeed before anything is written; on any failure the
     * previous catalog stays exactly as it was (last good wins). Only the
     * catalog database is touched, never the user's beers.
     */
    suspend fun refresh(): RefreshResult
}
