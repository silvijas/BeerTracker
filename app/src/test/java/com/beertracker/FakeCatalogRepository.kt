package com.beertracker

import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.CatalogStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeCatalogRepository : CatalogRepository {

    private val products = MutableStateFlow<List<CatalogProduct>>(emptyList())
    val status = MutableStateFlow<CatalogStatus?>(null)
    var lookups = 0
        private set

    fun add(product: CatalogProduct) {
        products.update { it + product }
    }

    /**
     * When set, every lookup waits here before answering. A test can then
     * act (type into the form, load a beer) while a lookup is still "on its
     * way", and release it afterwards by completing the deferred.
     */
    var holdLookupsUntil: CompletableDeferred<Unit>? = null

    override suspend fun findByArticleNumber(raw: String): CatalogProduct? {
        lookups += 1
        holdLookupsUntil?.await()
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        return products.value.find { it.articleNumber == digits || it.articleNumberShort == digits }
    }

    override fun observeStatus(): Flow<CatalogStatus?> = status

    override fun observeProducts(): Flow<List<CatalogProduct>> = products
}
