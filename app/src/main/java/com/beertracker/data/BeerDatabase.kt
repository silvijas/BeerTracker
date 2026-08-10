package com.beertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BeerEntity::class], version = 3, exportSchema = true)
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

        /**
         * v2 to v3 moves grading from the old 5-10 scale to 1-5. No release
         * has real graded data yet, so every existing grade is cleared
         * instead of remapped; every other column is untouched.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE tried_beers SET grade = NULL WHERE grade IS NOT NULL")
            }
        }

        fun build(context: Context): BeerDatabase =
            Room.databaseBuilder(context, BeerDatabase::class.java, "beertracker.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
