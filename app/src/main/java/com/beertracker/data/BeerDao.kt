package com.beertracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BeerDao {
    @Query("SELECT * FROM tried_beers")
    fun observeAll(): Flow<List<BeerEntity>>

    @Query("SELECT * FROM tried_beers WHERE id = :id")
    suspend fun getById(id: String): BeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BeerEntity)

    @Query("DELETE FROM tried_beers WHERE id = :id")
    suspend fun deleteById(id: String)
}
