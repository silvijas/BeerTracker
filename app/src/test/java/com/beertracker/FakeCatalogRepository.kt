package com.beertracker

import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.CatalogStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCatalogRepository : CatalogRepository {

    private val products = mutableListOf<CatalogProduct>()
    val status = MutableStateFlow<CatalogStatus?>(null)
    var lookups = 0
        private set

    fun add(product: CatalogProduct) {
        products.add(product)
    }

    override suspend fun findByArticleNumber(raw: String): CatalogProduct? {
        lookups += 1
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        return products.find { it.articleNumber == digits || it.articleNumberShort == digits }
    }

    override fun observeStatus(): Flow<CatalogStatus?> = status
}
