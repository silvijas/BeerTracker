package com.beertracker

import android.app.Application
import android.content.Context
import com.beertracker.data.BeerDatabase
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogImporter
import com.beertracker.data.RoomBeerRepository
import com.beertracker.data.RoomCatalogRepository
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val db = BeerDatabase.build(context)
    val beerRepository: BeerRepository = RoomBeerRepository(db.beerDao())

    private val catalogDb = CatalogDatabase.build(context)
    val catalogRepository: CatalogRepository = RoomCatalogRepository(catalogDb.catalogDao())
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
        }
    }
}
