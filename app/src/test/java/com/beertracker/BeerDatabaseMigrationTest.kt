package com.beertracker

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.beertracker.data.BeerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BeerDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BeerDatabase::class.java,
    )

    @Test
    fun `migrating 1 to 2 preserves an existing beer and defaults imageUrl to null`() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO tried_beers (id, name, brewery, type, alcoholPercent, volumeMl, " +
                    "price, grade, tried, note, aftertaste, goesWellWith, buyAgain, favourite, " +
                    "dateAdded, catalogArticleNumber, addedBy) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    "a", "Punk IPA", "BrewDog", "IPA", 5.6, 330, 29.5,
                    9, 1, "hoppy", "citrus bitter", "Red meat\u001FDessert", 1, 1,
                    12345L, "1324515", "Alex",
                ),
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, BeerDatabase.MIGRATION_1_2)

        db.query(
            "SELECT id, name, brewery, type, alcoholPercent, volumeMl, price, grade, tried, " +
                "note, aftertaste, goesWellWith, buyAgain, favourite, dateAdded, " +
                "catalogArticleNumber, addedBy, imageUrl FROM tried_beers",
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("a", cursor.getString(0))
            assertEquals("Punk IPA", cursor.getString(1))
            assertEquals("BrewDog", cursor.getString(2))
            assertEquals("IPA", cursor.getString(3))
            assertEquals(5.6, cursor.getDouble(4), 0.0)
            assertEquals(330, cursor.getInt(5))
            assertEquals(29.5, cursor.getDouble(6), 0.0)
            assertEquals(9, cursor.getInt(7))
            assertEquals(1, cursor.getInt(8))
            assertEquals("hoppy", cursor.getString(9))
            assertEquals("citrus bitter", cursor.getString(10))
            assertEquals("Red meat\u001FDessert", cursor.getString(11))
            assertEquals(1, cursor.getInt(12))
            assertEquals(1, cursor.getInt(13))
            assertEquals(12345L, cursor.getLong(14))
            assertEquals("1324515", cursor.getString(15))
            assertEquals("Alex", cursor.getString(16))
            assertTrue(cursor.isNull(17))
        }
    }

    @Test
    fun `migrating 2 to 3 adds photoUri and remaps the old pairing values`() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(INSERT_V1, arrayOf(
                "a", "Punk IPA", "BrewDog", "IPA", 5.6, 330, 29.5,
                4, 1, "hoppy", "citrus bitter",
                "Red meat\u001FSalmon\u001FWhite fish\u001FDessert\u001FTacos",
                1, 1, 12345L, "1324515", "Alex",
            ))
            db.execSQL(INSERT_V1, arrayOf(
                "b", "Pasta Beer", "", "", null, null, null,
                null, 0, "", "", "Pasta white sauce\u001FPasta tomato sauce",
                0, 0, 1L, null, null,
            ))
        }

        val db = helper.runMigrationsAndValidate(
            DB_NAME, 3, true, BeerDatabase.MIGRATION_1_2, BeerDatabase.MIGRATION_2_3,
        )

        db.query("SELECT id, goesWellWith, photoUri FROM tried_beers ORDER BY id").use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("a", cursor.getString(0))
            assertEquals("Beef\u001FFish\u001FDessert\u001FTacos", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.moveToNext())
            assertEquals("b", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
    }

    @Test
    fun `migrating 2 to 3 leaves every other column alone`() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(INSERT_V1, arrayOf(
                "a", "Punk IPA", "BrewDog", "IPA", 5.6, 330, 29.5,
                4, 1, "hoppy", "citrus bitter", "Dessert", 1, 1,
                12345L, "1324515", "Alex",
            ))
        }

        val db = helper.runMigrationsAndValidate(
            DB_NAME, 3, true, BeerDatabase.MIGRATION_1_2, BeerDatabase.MIGRATION_2_3,
        )

        db.query(
            "SELECT name, brewery, type, alcoholPercent, volumeMl, price, grade, tried, " +
                "note, aftertaste, goesWellWith, buyAgain, favourite, dateAdded, " +
                "catalogArticleNumber, addedBy FROM tried_beers",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Punk IPA", cursor.getString(0))
            assertEquals("BrewDog", cursor.getString(1))
            assertEquals("IPA", cursor.getString(2))
            assertEquals(5.6, cursor.getDouble(3), 0.0)
            assertEquals(330, cursor.getInt(4))
            assertEquals(29.5, cursor.getDouble(5), 0.0)
            assertEquals(4, cursor.getInt(6))
            assertEquals(1, cursor.getInt(7))
            assertEquals("hoppy", cursor.getString(8))
            assertEquals("citrus bitter", cursor.getString(9))
            assertEquals("Dessert", cursor.getString(10))
            assertEquals(1, cursor.getInt(11))
            assertEquals(1, cursor.getInt(12))
            assertEquals(12345L, cursor.getLong(13))
            assertEquals("1324515", cursor.getString(14))
            assertEquals("Alex", cursor.getString(15))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val INSERT_V1 =
            "INSERT INTO tried_beers (id, name, brewery, type, alcoholPercent, volumeMl, " +
                "price, grade, tried, note, aftertaste, goesWellWith, buyAgain, favourite, " +
                "dateAdded, catalogArticleNumber, addedBy) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    }
}
