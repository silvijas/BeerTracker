package com.beertracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.beertracker.domain.CatalogProduct

@Entity(tableName = "catalog_beers")
data class CatalogBeerEntity(
    @PrimaryKey val articleNumber: String,
    val articleNumberShort: String?,
    val name: String,
    val brewery: String,
    val type: String,
    val alcoholPercent: Double?,
    val volumeMl: Int?,
    val price: Double?,
    val country: String?,
    val imageUrl: String?,
    val pairings: List<String>,
)

/**
 * One row (id is always 1) describing where the catalog contents came from:
 * which bundled seed version was imported, how many beers are loaded, and
 * when the last successful in-app refresh ran (null if never).
 */
@Entity(tableName = "catalog_metadata")
data class CatalogMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val snapshotVersion: String?,
    val beerCount: Int,
    val lastRefreshUtc: Long?,
)

fun CatalogBeerEntity.toDomain() = CatalogProduct(
    articleNumber = articleNumber,
    articleNumberShort = articleNumberShort,
    name = name,
    brewery = brewery,
    type = type,
    alcoholPercent = alcoholPercent,
    volumeMl = volumeMl,
    price = price,
    country = country,
    imageUrl = imageUrl,
    pairings = pairings,
)

fun CatalogProduct.toEntity() = CatalogBeerEntity(
    articleNumber = articleNumber,
    articleNumberShort = articleNumberShort,
    name = name,
    brewery = brewery,
    type = type,
    alcoholPercent = alcoholPercent,
    volumeMl = volumeMl,
    price = price,
    country = country,
    imageUrl = imageUrl,
    pairings = pairings,
)
