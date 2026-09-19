package com.beertracker.data

import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.flow.Flow

/**
 * The repository every screen uses once sync exists. Reads come straight
 * from the local (Room) repository. Writes go to Room first, so a save can
 * never be lost to a Firebase problem, and are then pushed through the
 * engine, which ignores them while the phone is not paired.
 */
class SyncingBeerRepository(
    private val local: BeerRepository,
    private val engine: CellarSyncEngine,
) : BeerRepository {

    override fun observeBeers(): Flow<List<TriedBeer>> = local.observeBeers()

    override suspend fun getBeer(id: String): TriedBeer? = local.getBeer(id)

    override suspend fun addBeer(beer: TriedBeer) {
        val stamped = if (beer.addedBy == null) beer.copy(addedBy = engine.pairedUserId) else beer
        local.addBeer(stamped)
        engine.pushBeer(stamped)
    }

    override suspend fun updateBeer(beer: TriedBeer) {
        local.updateBeer(beer)
        engine.pushBeer(beer)
    }

    override suspend fun deleteBeer(id: String) {
        local.deleteBeer(id)
        engine.pushDelete(id)
    }
}
