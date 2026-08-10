package com.beertracker

import android.app.Application
import android.content.Context
import android.util.Log
import com.beertracker.data.BeerDatabase
import com.beertracker.data.BeerPhotoStore
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogImporter
import com.beertracker.data.DefaultCatalogRefresher
import com.beertracker.data.PrefsSettingsRepository
import com.beertracker.data.RoomBeerRepository
import com.beertracker.data.RoomCatalogRepository
import com.beertracker.data.SystembolagetCatalogFetcher
import com.beertracker.data.isNetworkAvailable
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CatalogRefresher
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.SettingsRepository
import com.beertracker.domain.shouldAutoRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val db = BeerDatabase.build(context)
    val beerPhotoStore = BeerPhotoStore(context.filesDir)
    val beerRepository: BeerRepository = RoomBeerRepository(db.beerDao(), beerPhotoStore)

    private val catalogDb = CatalogDatabase.build(context)
    val catalogRepository: CatalogRepository = RoomCatalogRepository(catalogDb.catalogDao())
    val catalogRefresher: CatalogRefresher =
        DefaultCatalogRefresher(catalogDb, SystembolagetCatalogFetcher())
    val settingsRepository: SettingsRepository = PrefsSettingsRepository(context)
    val catalogImporter = CatalogImporter(
        readAsset = {
            context.assets.open("catalog/beers.json").bufferedReader().use { it.readText() }
        },
        database = catalogDb,
    )
}

class BeerApp : Application() {
    lateinit var container: AppContainer
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.catalogImporter.importIfNeeded()
            deleteOrphanPhotos()
            autoRefreshCatalog()
        }
    }

    /**
     * Reclaims photo files no beer points at any more: replaced photos,
     * removed photos, and photos taken on an add form that was abandoned.
     * Doing it here rather than at the moment of replacement means an
     * abandoned edit can never delete a file the saved row still uses.
     */
    private suspend fun deleteOrphanPhotos() {
        try {
            val referenced = container.beerRepository.observeBeers().first()
                .mapNotNull { it.photoUri }
                .toSet()
            container.beerPhotoStore.deleteOrphans(referenced)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Orphan photo sweep failed, files stay as they are", error)
        }
    }

    /**
     * Weekly, silent, launch-triggered catalog refresh. Failures are logged
     * and ignored here, same as inside the refresher; the user is never
     * interrupted and a refresh problem can never crash app startup.
     */
    private suspend fun autoRefreshCatalog() {
        try {
            val status = container.catalogRepository.observeStatus().first()
            val due = shouldAutoRefresh(status?.lastRefreshUtc, System.currentTimeMillis())
            if (due && isNetworkAvailable(this)) {
                container.catalogRefresher.refresh()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Auto refresh check failed, catalog stays as is", error)
        }
    }

    private companion object {
        const val TAG = "BeerApp"
    }
}
