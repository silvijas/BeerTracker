package com.beertracker

import android.app.Application
import android.content.Context
import com.beertracker.data.BeerDatabase
import com.beertracker.data.RoomBeerRepository
import com.beertracker.domain.BeerRepository

class AppContainer(context: Context) {
    private val db = BeerDatabase.build(context)
    val beerRepository: BeerRepository = RoomBeerRepository(db.beerDao())
}

class BeerApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
