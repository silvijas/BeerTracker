package com.beertracker.data

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException

class CatalogImporter(
    private val readAsset: () -> String,
    private val database: CatalogDatabase,
) {

    /**
     * Imports the bundled seed when the catalog is empty or was built from a
     * different seed version. A refresh (Task 6) keeps the seed version and
     * only bumps lastRefreshUtc, so refreshed data survives relaunches and is
     * only replaced when a new APK ships a new seed. Never throws: an app
     * without a catalog is still a working beer tracker.
     */
    suspend fun importIfNeeded() {
        try {
            val seed = parseCatalogAsset(readAsset())
            val dao = database.catalogDao()
            val metadata = dao.getMetadata()
            if (metadata?.snapshotVersion == seed.snapshotVersion && dao.count() > 0) return
            database.withTransaction {
                dao.deleteAll()
                dao.insertAll(seed.beers.map { it.toEntity() })
                dao.setMetadata(
                    CatalogMetadataEntity(
                        snapshotVersion = seed.snapshotVersion,
                        beerCount = seed.beers.size,
                        lastRefreshUtc = null,
                    ),
                )
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("CatalogImporter", "Seed import failed, catalog lookups stay empty", error)
        }
    }
}
