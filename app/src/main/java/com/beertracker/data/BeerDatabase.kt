package com.beertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BeerEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class BeerDatabase : RoomDatabase() {
    abstract fun beerDao(): BeerDao

    companion object {
        /**
         * v1 to v2 adds the nullable imageUrl column. The user's phone holds
         * real beers at version 1, so this must stay a non-destructive ALTER
         * TABLE. Never attach fallbackToDestructiveMigration to this
         * database; losing user data is never acceptable here.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tried_beers ADD COLUMN imageUrl TEXT")
            }
        }

        fun build(context: Context): BeerDatabase =
            Room.databaseBuilder(context, BeerDatabase::class.java, "beertracker.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
