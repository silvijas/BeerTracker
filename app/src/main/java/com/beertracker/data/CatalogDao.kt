package com.beertracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    /**
     * A short number is shared by every packaging of the same beer (over 200
     * such pairs in the catalog), and a shelf label does not say which one
     * it is. Ordering by the full number makes the pick predictable: the
     * same short number always resolves to the same product, the one with
     * the lowest full article number. Without the ORDER BY, SQLite returns
     * whichever row it happens to visit first.
     */
    @Query(
        "SELECT * FROM catalog_beers " +
            "WHERE articleNumber = :number OR articleNumberShort = :number " +
            "ORDER BY articleNumber LIMIT 1",
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
