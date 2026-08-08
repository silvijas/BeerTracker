package com.beertracker.domain

const val CATALOG_REFRESH_INTERVAL_MS: Long = 7L * 24 * 60 * 60 * 1000

/**
 * True when the catalog has never been refreshed from the network (a fresh
 * install running on the bundled seed) or the last refresh is a week old.
 */
fun shouldAutoRefresh(lastRefreshUtc: Long?, nowUtc: Long): Boolean =
    lastRefreshUtc == null || nowUtc - lastRefreshUtc >= CATALOG_REFRESH_INTERVAL_MS
