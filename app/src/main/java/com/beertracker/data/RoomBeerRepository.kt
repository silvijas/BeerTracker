package com.beertracker.data

import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBeerRepository(private val dao: BeerDao) : BeerRepository {

    override fun observeBeers(): Flow<List<TriedBeer>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBeer(id: String): TriedBeer? = dao.getById(id)?.toDomain()

    override suspend fun addBeer(beer: TriedBeer) = dao.upsert(beer.toEntity())

    override suspend fun updateBeer(beer: TriedBeer) = dao.upsert(beer.toEntity())

    override suspend fun deleteBeer(id: String) = dao.deleteById(id)
}
