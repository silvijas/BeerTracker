package com.beertracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.BeerDatabase
import com.beertracker.data.BeerPhotoStore
import com.beertracker.data.RoomBeerRepository
import java.io.File
import java.net.URI
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BeerDaoTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var db: BeerDatabase
    private lateinit var repo: RoomBeerRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BeerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomBeerRepository(db.beerDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `round trip preserves all fields`() = runTest {
        val original = beer(
            id = "a", name = "Punk IPA", brewery = "BrewDog", type = "IPA",
            alcoholPercent = 5.6, volumeMl = 330, price = 29.5, grade = 4, tried = true,
            note = "hoppy", aftertaste = "citrus bitter",
            goesWellWith = listOf("Red meat", "Dessert"),
            buyAgain = true, favourite = true, dateAdded = 12345L)
        repo.addBeer(original)
        assertEquals(original, repo.getBeer("a"))
        assertEquals(listOf(original), repo.observeBeers().first())
    }

    @Test
    fun `round trip preserves an untried beer with no grade`() = runTest {
        val original = beer(
            id = "u", name = "Shelf Find Wheat", grade = null, tried = false, dateAdded = 500L)
        repo.addBeer(original)
        assertEquals(original, repo.getBeer("u"))
        assertNull(repo.getBeer("u")?.grade)
        assertFalse(repo.getBeer("u")!!.tried)
    }

    @Test
    fun `round trip preserves a tried beer with no grade`() = runTest {
        val original = beer(
            id = "t", name = "Tasted At A Bar", grade = null, tried = true, dateAdded = 600L)
        repo.addBeer(original)
        assertEquals(original, repo.getBeer("t"))
        assertNull(repo.getBeer("t")?.grade)
        assertTrue(repo.getBeer("t")!!.tried)
    }

    @Test
    fun `update replaces and delete removes`() = runTest {
        repo.addBeer(beer(id = "a", grade = 3))
        repo.updateBeer(beer(id = "a", grade = 5))
        assertEquals(5, repo.getBeer("a")?.grade)
        repo.updateBeer(beer(id = "a", grade = null, tried = false))
        assertNull(repo.getBeer("a")?.grade)
        repo.deleteBeer("a")
        assertNull(repo.getBeer("a"))
        assertEquals(emptyList<Any>(), repo.observeBeers().first())
    }

    @Test
    fun `deleting a beer deletes its photo file`() = runTest {
        val photoStore = BeerPhotoStore(temporaryFolder.root)
        val uri = photoStore.save("photo".byteInputStream())
        val repoWithPhotos = RoomBeerRepository(db.beerDao(), photoStore)
        repoWithPhotos.addBeer(beer(id = "a").copy(photoUri = uri))

        repoWithPhotos.deleteBeer("a")

        assertFalse(File(URI(uri)).exists())
    }

    @Test
    fun `deleting a beer with no photo does not throw`() = runTest {
        val repoWithPhotos = RoomBeerRepository(
            db.beerDao(),
            BeerPhotoStore(temporaryFolder.root),
        )
        repoWithPhotos.addBeer(beer(id = "a"))

        repoWithPhotos.deleteBeer("a")

        assertNull(repoWithPhotos.getBeer("a"))
    }

    @Test
    fun `photoUri round trips`() = runTest {
        repo.addBeer(beer(id = "a").copy(photoUri = "file:///photos/a.jpg"))
        assertEquals("file:///photos/a.jpg", repo.getBeer("a")?.photoUri)
    }
}
