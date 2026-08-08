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
                    12345L, "1324515", null,
                ),
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, BeerDatabase.MIGRATION_1_2)

        db.query("SELECT id, name, grade, goesWellWith, catalogArticleNumber, imageUrl FROM tried_beers").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("a", cursor.getString(0))
            assertEquals("Punk IPA", cursor.getString(1))
            assertEquals(9, cursor.getInt(2))
            assertEquals("Red meat\u001FDessert", cursor.getString(3))
            assertEquals("1324515", cursor.getString(4))
            assertTrue(cursor.isNull(5))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
