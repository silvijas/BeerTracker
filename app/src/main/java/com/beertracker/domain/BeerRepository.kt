package com.beertracker.domain

import kotlinx.coroutines.flow.Flow

interface BeerRepository {
    fun observeBeers(): Flow<List<TriedBeer>>
    suspend fun getBeer(id: String): TriedBeer?
    suspend fun addBeer(beer: TriedBeer)
    suspend fun updateBeer(beer: TriedBeer)
    suspend fun deleteBeer(id: String)
}
