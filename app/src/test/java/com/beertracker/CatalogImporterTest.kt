package com.beertracker

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogImporter
import com.beertracker.data.parseCatalogAsset
import com.beertracker.data.toEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CatalogImporterTest {

    private lateinit var db: CatalogDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun seedJson(version: String, vararg articleNumbers: String): String {
        val beers = articleNumbers.joinToString(",") { number ->
            """{"articleNumber": "$number", "articleNumberShort": null, "name": "Beer $number",
                "brewery": "Brew", "type": "Ale", "alcoholPercent": 5.0, "volumeMl": 330,
                "price": 20.0, "country": "Sverige", "imageUrl": null}"""
        }
        return """{"snapshotVersion": "$version", "beers": [$beers]}"""
    }

    @Test
    fun `first import fills the catalog and records the version`() = runTest {
        CatalogImporter({ seedJson("2026-08-08", "101", "202") }, db).importIfNeeded()
        assertEquals(2, db.catalogDao().count())
        val metadata = db.catalogDao().getMetadata()
        assertEquals("2026-08-08", metadata?.snapshotVersion)
        assertEquals(2, metadata?.beerCount)
        assertNull(metadata?.lastRefreshUtc)
    }

    @Test
    fun `import with the same version is skipped`() = runTest {
        CatalogImporter({ seedJson("2026-08-08", "101") }, db).importIfNeeded()
        // A marker row inserted behind the importer's back survives a rerun
        // with the same seed version, proving the rerun was skipped.
        db.catalogDao().insertAll(listOf(catalogProduct(articleNumber = "999").toEntity()))
        CatalogImporter({ seedJson("2026-08-08", "101") }, db).importIfNeeded()
        assertNotNull(db.catalogDao().findByNumber("999"))
        assertEquals(2, db.catalogDao().count())
    }

    @Test
    fun `import with a new version wipes and replaces the catalog`() = runTest {
        CatalogImporter({ seedJson("2026-08-08", "101", "202") }, db).importIfNeeded()
        CatalogImporter({ seedJson("2026-09-01", "303") }, db).importIfNeeded()
        assertEquals(1, db.catalogDao().count())
        assertNull(db.catalogDao().findByNumber("101"))
        assertNotNull(db.catalogDao().findByNumber("303"))
        assertEquals("2026-09-01", db.catalogDao().getMetadata()?.snapshotVersion)
    }

    @Test
    fun `a broken asset never throws and leaves the catalog untouched`() = runTest {
        CatalogImporter({ "this is not json" }, db).importIfNeeded()
        assertEquals(0, db.catalogDao().count())
        CatalogImporter({ seedJson("2026-08-08", "101") }, db).importIfNeeded()
        CatalogImporter({ "this is not json" }, db).importIfNeeded()
        assertEquals(1, db.catalogDao().count())
    }

    @Test
    fun `parses and imports the real bundled catalog asset`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val assetText = context.assets.open("catalog/beers.json").bufferedReader().use { it.readText() }

        val seed = parseCatalogAsset(assetText)
        assertEquals(4970, seed.beers.size)
        assertTrue(seed.snapshotVersion.isNotBlank())

        CatalogImporter({ assetText }, db).importIfNeeded()
        assertEquals(4970, db.catalogDao().count())
        assertEquals("1462", db.catalogDao().findByNumber("146212")?.articleNumberShort)
    }
}
