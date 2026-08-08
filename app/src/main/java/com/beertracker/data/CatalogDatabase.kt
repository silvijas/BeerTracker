package com.beertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The catalog database is a disposable cache, fed by the bundled seed asset
 * and the in-app refresh. It lives in its own file on purpose: nothing that
 * happens here can touch the user's beers in beertracker.db. Because every
 * row can be rebuilt from the asset, destructive migration is fine HERE and
 * only here; schema history is not tracked (exportSchema false).
 */
@Database(
    entities = [CatalogBeerEntity::class, CatalogMetadataEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        fun build(context: Context): CatalogDatabase =
            Room.databaseBuilder(context, CatalogDatabase::class.java, "catalog.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
