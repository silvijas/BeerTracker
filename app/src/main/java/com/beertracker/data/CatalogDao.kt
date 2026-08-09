package com.beertracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Query(
        "SELECT * FROM catalog_beers " +
            "WHERE articleNumber = :number OR articleNumberShort = :number LIMIT 1",
    )
    suspend fun findByNumber(number: String): CatalogBeerEntity?

    @Query("SELECT * FROM catalog_beers")
    fun observeAll(): Flow<List<CatalogBeerEntity>>

    @Query("SELECT COUNT(*) FROM catalog_beers")
    suspend fun count(): Int

    @Query("DELETE FROM catalog_beers")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(beers: List<CatalogBeerEntity>)

    @Query("SELECT * FROM catalog_metadata WHERE id = 1")
    suspend fun getMetadata(): CatalogMetadataEntity?

    @Query("SELECT * FROM catalog_metadata WHERE id = 1")
    fun observeMetadata(): Flow<CatalogMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(metadata: CatalogMetadataEntity)
}
