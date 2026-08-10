package com.beertracker

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeBeerRepositoryTest {

    @Test
    fun `added beer appears in observed list and by id`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a"))
        assertEquals(listOf("a"), repo.observeBeers().first().map { it.id })
        assertEquals("a", repo.getBeer("a")?.id)
    }

    @Test
    fun `update replaces the beer with the same id`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", grade = 3))
        repo.updateBeer(beer(id = "a", grade = 5))
        assertEquals(5, repo.getBeer("a")?.grade)
        assertEquals(1, repo.observeBeers().first().size)
    }

    @Test
    fun `delete removes the beer`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a"))
        repo.deleteBeer("a")
        assertNull(repo.getBeer("a"))
        assertEquals(0, repo.observeBeers().first().size)
    }
}
