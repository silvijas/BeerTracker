package com.beertracker

import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeBeerRepository : BeerRepository {
    private val beers = MutableStateFlow<Map<String, TriedBeer>>(emptyMap())

    override fun observeBeers(): Flow<List<TriedBeer>> = beers.map { it.values.toList() }

    override suspend fun getBeer(id: String): TriedBeer? = beers.value[id]

    override suspend fun addBeer(beer: TriedBeer) = beers.update { it + (beer.id to beer) }

    override suspend fun updateBeer(beer: TriedBeer) = beers.update { it + (beer.id to beer) }

    override suspend fun deleteBeer(id: String) = beers.update { it - id }
}
