package com.beertracker

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogFetcher
import com.beertracker.data.CatalogMetadataEntity
import com.beertracker.data.DefaultCatalogRefresher
import com.beertracker.data.toEntity
import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.RefreshResult
import java.io.IOException
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
class DefaultCatalogRefresherTest {

    private lateinit var db: CatalogDatabase

    @Before
    fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.catalogDao().insertAll(listOf(catalogProduct(articleNumber = "111", name = "Stale Beer").toEntity()))
        db.catalogDao().setMetadata(
            CatalogMetadataEntity(snapshotVersion = "2026-08-01", beerCount = 1, lastRefreshUtc = null),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun fetcherReturning(beers: List<CatalogProduct>) = object : CatalogFetcher {
        override suspend fun fetchAllBeers(): List<CatalogProduct> = beers
    }

    private fun manyBeers(count: Int): List<CatalogProduct> = List(count) { index ->
        catalogProduct(articleNumber = (1_000_000 + index).toString(), articleNumberShort = null)
    }

    @Test
    fun `a successful refresh replaces the catalog and records count and time`() = runTest {
        val refresher = DefaultCatalogRefresher(db, fetcherReturning(manyBeers(600)), clock = { 999L })

        val result = refresher.refresh()

        assertEquals(RefreshResult.Success(beerCount = 600, refreshedUtc = 999L), result)
        assertEquals(600, db.catalogDao().count())
        assertNull(db.catalogDao().findByNumber("111"))
        val metadata = db.catalogDao().getMetadata()
        assertEquals(600, metadata?.beerCount)
        assertEquals(999L, metadata?.lastRefreshUtc)
        assertEquals("2026-08-01", metadata?.snapshotVersion)
    }

    @Test
    fun `a failed fetch keeps the previous catalog untouched`() = runTest {
        val refresher = DefaultCatalogRefresher(
            db,
            object : CatalogFetcher {
                override suspend fun fetchAllBeers(): List<CatalogProduct> =
                    throw IOException("HTTP 503")
            },
        )

        val result = refresher.refresh()

        assertTrue(result is RefreshResult.Failure)
        assertEquals(1, db.catalogDao().count())
        assertNotNull(db.catalogDao().findByNumber("111"))
        assertEquals(1, db.catalogDao().getMetadata()?.beerCount)
        assertNull(db.catalogDao().getMetadata()?.lastRefreshUtc)
    }

    @Test
    fun `an implausibly small answer is treated as a failure`() = runTest {
        val refresher = DefaultCatalogRefresher(db, fetcherReturning(manyBeers(3)))

        val result = refresher.refresh()

        assertTrue(result is RefreshResult.Failure)
        assertEquals(1, db.catalogDao().count())
        assertNotNull(db.catalogDao().findByNumber("111"))
    }
}
