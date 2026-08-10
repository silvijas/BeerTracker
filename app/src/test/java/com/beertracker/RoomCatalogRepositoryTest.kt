package com.beertracker

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogMetadataEntity
import com.beertracker.data.RoomCatalogRepository
import com.beertracker.data.toEntity
import com.beertracker.domain.CatalogStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RoomCatalogRepositoryTest {

    private lateinit var db: CatalogDatabase
    private lateinit var repo: RoomCatalogRepository

    @Before
    fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomCatalogRepository(db.catalogDao())
        db.catalogDao().insertAll(
            listOf(
                catalogProduct().toEntity(),
                catalogProduct(
                    articleNumber = "1000501",
                    articleNumberShort = "10005",
                    name = "Second Beer",
                ).toEntity(),
            ),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `finds by full article number`() = runTest {
        assertEquals("Omnipollo Prodigal Pale Ale", repo.findByArticleNumber("1324515")?.name)
    }

    @Test
    fun `pairings round trip through the catalog database`() = runTest {
        db.catalogDao().insertAll(
            listOf(
                catalogProduct(
                    articleNumber = "2000001",
                    articleNumberShort = "20000",
                    pairings = listOf("Pork", "Social drink"),
                ).toEntity(),
            ),
        )
        assertEquals(
            listOf("Pork", "Social drink"),
            repo.findByArticleNumber("2000001")?.pairings,
        )
    }

    @Test
    fun `a product with no pairings comes back with an empty list`() = runTest {
        assertEquals(emptyList<String>(), repo.findByArticleNumber("1324515")?.pairings)
    }

    @Test
    fun `finds by short article number`() = runTest {
        assertEquals("Omnipollo Prodigal Pale Ale", repo.findByArticleNumber("13245")?.name)
    }

    @Test
    fun `ignores surrounding whitespace and non digits`() = runTest {
        assertEquals("Second Beer", repo.findByArticleNumber(" Nr 10005 ")?.name)
    }

    @Test
    fun `strips leading zeros as a second attempt`() = runTest {
        assertEquals("Omnipollo Prodigal Pale Ale", repo.findByArticleNumber("013245")?.name)
    }

    @Test
    fun `unknown or empty input gives null`() = runTest {
        assertNull(repo.findByArticleNumber("999999"))
        assertNull(repo.findByArticleNumber(""))
        assertNull(repo.findByArticleNumber("no digits here"))
    }

    @Test
    fun `observeProducts emits every catalog row and reflects new imports`() = runTest {
        assertEquals(
            listOf("Omnipollo Prodigal Pale Ale", "Second Beer"),
            repo.observeProducts().first().map { it.name }.sorted(),
        )
        db.catalogDao().insertAll(
            listOf(
                catalogProduct(
                    articleNumber = "7700101",
                    articleNumberShort = "77001",
                    name = "Third Beer",
                ).toEntity(),
            ),
        )
        assertEquals(3, repo.observeProducts().first().size)
    }

    @Test
    fun `status is null before metadata exists and mirrors it afterwards`() = runTest {
        assertNull(repo.observeStatus().first())
        db.catalogDao().setMetadata(
            CatalogMetadataEntity(snapshotVersion = "2026-08-08", beerCount = 2, lastRefreshUtc = 123L),
        )
        assertEquals(CatalogStatus(beerCount = 2, lastRefreshUtc = 123L), repo.observeStatus().first())
    }
}
