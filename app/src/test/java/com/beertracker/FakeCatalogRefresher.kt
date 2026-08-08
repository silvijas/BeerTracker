package com.beertracker

import com.beertracker.domain.CatalogRefresher
import com.beertracker.domain.RefreshResult
import kotlinx.coroutines.CompletableDeferred

class FakeCatalogRefresher : CatalogRefresher {

    var result: RefreshResult = RefreshResult.Success(beerCount = 1534, refreshedUtc = 0L)
    var refreshCalls = 0
        private set

    /** When set, refresh() suspends until the gate completes, so tests can
     * observe the Refreshing state and reentrancy behavior. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun refresh(): RefreshResult {
        refreshCalls += 1
        gate?.await()
        return result
    }
}
