package com.beertracker.data

import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.CatalogStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCatalogRepository(private val dao: CatalogDao) : CatalogRepository {

    override suspend fun findByArticleNumber(raw: String): CatalogProduct? {
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        val candidates = buildList {
            add(digits)
            val withoutLeadingZeros = digits.trimStart('0')
            if (withoutLeadingZeros.isNotEmpty() && withoutLeadingZeros != digits) {
                add(withoutLeadingZeros)
            }
        }
        for (candidate in candidates) {
            dao.findByNumber(candidate)?.let { return it.toDomain() }
        }
        return null
    }

    override fun observeProducts(): Flow<List<CatalogProduct>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeStatus(): Flow<CatalogStatus?> =
        dao.observeMetadata().map { metadata ->
            metadata?.let {
                CatalogStatus(beerCount = it.beerCount, lastRefreshUtc = it.lastRefreshUtc)
            }
        }
}
