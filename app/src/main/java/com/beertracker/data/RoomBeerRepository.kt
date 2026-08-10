package com.beertracker.data

import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBeerRepository(
    private val dao: BeerDao,
    private val photoStore: BeerPhotoStore? = null,
) : BeerRepository {

    override fun observeBeers(): Flow<List<TriedBeer>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBeer(id: String): TriedBeer? = dao.getById(id)?.toDomain()

    override suspend fun addBeer(beer: TriedBeer) = dao.upsert(beer.toEntity())

    override suspend fun updateBeer(beer: TriedBeer) = dao.upsert(beer.toEntity())

    /**
     * The row is definitively gone, so its photo can go with it. This is the
     * one place a photo file is deleted eagerly; every other case is left to
     * BeerPhotoStore.deleteOrphans, because an abandoned edit must never
     * delete a file the saved row still points at.
     */
    override suspend fun deleteBeer(id: String) {
        val photoUri = dao.getById(id)?.photoUri
        dao.deleteById(id)
        photoStore?.delete(photoUri)
    }
}
