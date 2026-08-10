package com.beertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The unit separator Converters joins goesWellWith with, in BeerEntity.kt. */
private const val PAIRING_SEPARATOR = "\u001F"

/**
 * The app's old invented pairing list, mapped onto the Systembolaget
 * vocabulary that replaced it. A null value means the old value has no
 * equivalent and is dropped, which the user chose over keeping it as an
 * iconless text chip.
 */
private val LEGACY_PAIRINGS = mapOf(
    "Red meat" to "Beef",
    "Salmon" to "Fish",
    "White fish" to "Fish",
    "Pasta white sauce" to null,
    "Pasta tomato sauce" to null,
)

/**
 * Rewrites one stored goesWellWith value onto the new vocabulary. Done in
 * Kotlin rather than SQL string replacement because two old values collapse
 * onto one new value, so the result needs real deduplication.
 */
internal fun remapPairings(stored: String): String {
    if (stored.isEmpty()) return stored
    return stored.split(PAIRING_SEPARATOR)
        .mapNotNull { if (LEGACY_PAIRINGS.containsKey(it)) LEGACY_PAIRINGS[it] else it }
        .distinct()
        .joinToString(PAIRING_SEPARATOR)
}

@Database(entities = [BeerEntity::class], version = 4, exportSchema = true)
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

        /**
         * v3 to v4 adds the nullable photoUri column and rewrites stored
         * pairings onto the Systembolaget vocabulary. Non destructive on
         * every other column, for the same reason as above.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tried_beers ADD COLUMN photoUri TEXT")
                // Collected first, then applied, so no UPDATE runs against
                // the table while a cursor is still open on it.
                val updates = mutableListOf<Pair<String, String>>()
                db.query("SELECT id, goesWellWith FROM tried_beers").use { cursor ->
                    while (cursor.moveToNext()) {
                        val stored = if (cursor.isNull(1)) "" else cursor.getString(1)
                        val remapped = remapPairings(stored)
                        if (remapped != stored) updates.add(cursor.getString(0) to remapped)
                    }
                }
                updates.forEach { (id, value) ->
                    db.execSQL(
                        "UPDATE tried_beers SET goesWellWith = ? WHERE id = ?",
                        arrayOf(value, id),
                    )
                }
            }
        }

        fun build(context: Context): BeerDatabase =
            Room.databaseBuilder(context, BeerDatabase::class.java, "beertracker.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
