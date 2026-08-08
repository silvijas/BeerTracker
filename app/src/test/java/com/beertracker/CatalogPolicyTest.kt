package com.beertracker

import com.beertracker.domain.CATALOG_REFRESH_INTERVAL_MS
import com.beertracker.domain.shouldAutoRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPolicyTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `never refreshed means refresh now`() {
        assertTrue(shouldAutoRefresh(lastRefreshUtc = null, nowUtc = now))
    }

    @Test
    fun `a recent refresh waits`() {
        val sixDaysAgo = now - (CATALOG_REFRESH_INTERVAL_MS - 86_400_000L)
        assertFalse(shouldAutoRefresh(lastRefreshUtc = sixDaysAgo, nowUtc = now))
    }

    @Test
    fun `exactly one week is due`() {
        assertTrue(shouldAutoRefresh(lastRefreshUtc = now - CATALOG_REFRESH_INTERVAL_MS, nowUtc = now))
    }

    @Test
    fun `older than one week is due`() {
        assertTrue(shouldAutoRefresh(lastRefreshUtc = 0L, nowUtc = now))
    }
}
