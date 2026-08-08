package com.beertracker

import android.app.Application
import android.content.Context
import android.util.Log
import com.beertracker.data.BeerDatabase
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogImporter
import com.beertracker.data.DefaultCatalogRefresher
import com.beertracker.data.RoomBeerRepository
import com.beertracker.data.RoomCatalogRepository
import com.beertracker.data.SystembolagetCatalogFetcher
import com.beertracker.data.isNetworkAvailable
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CatalogRefresher
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.shouldAutoRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val db = BeerDatabase.build(context)
    val beerRepository: BeerRepository = RoomBeerRepository(db.beerDao())

    private val catalogDb = CatalogDatabase.build(context)
    val catalogRepository: CatalogRepository = RoomCatalogRepository(catalogDb.catalogDao())
    val catalogRefresher: CatalogRefresher =
        DefaultCatalogRefresher(catalogDb, SystembolagetCatalogFetcher())
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
            autoRefreshCatalog()
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
