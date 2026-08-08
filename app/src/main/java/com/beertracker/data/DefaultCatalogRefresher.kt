package com.beertracker.data

import android.util.Log
import androidx.room.withTransaction
import com.beertracker.domain.CatalogRefresher
import com.beertracker.domain.RefreshResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A fetch that yields implausibly few beers (the assortment holds about
 * 1,500) is refused so a broken answer can never wipe a healthy catalog.
 */
internal const val MIN_PLAUSIBLE_BEER_COUNT = 500

class DefaultCatalogRefresher(
    private val database: CatalogDatabase,
    private val fetcher: CatalogFetcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : CatalogRefresher {

    /** One refresh at a time; a manual tap during the launch refresh waits. */
    private val refreshMutex = Mutex()

    override suspend fun refresh(): RefreshResult = refreshMutex.withLock {
        val beers = try {
            fetcher.fetchAllBeers()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Catalog refresh failed, keeping the previous catalog", error)
            return RefreshResult.Failure("Could not reach the Systembolaget catalog")
        }
        if (beers.size < MIN_PLAUSIBLE_BEER_COUNT) {
            Log.w(TAG, "Refusing implausible catalog of ${beers.size} beers")
            return RefreshResult.Failure("The catalog answer looked incomplete")
        }
        val now = clock()
        val dao = database.catalogDao()
        database.withTransaction {
            val previous = dao.getMetadata()
            dao.deleteAll()
            dao.insertAll(beers.map { it.toEntity() })
            dao.setMetadata(
                CatalogMetadataEntity(
                    snapshotVersion = previous?.snapshotVersion,
                    beerCount = beers.size,
                    lastRefreshUtc = now,
                ),
            )
        }
        RefreshResult.Success(beerCount = beers.size, refreshedUtc = now)
    }

    private companion object {
        const val TAG = "CatalogRefresher"
    }
}
