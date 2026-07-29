package com.beertracker

import com.beertracker.ui.DetailViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `beer flow emits the beer with the given id`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Falcon"))
        repo.addBeer(beer(id = "b", name = "Punk IPA"))
        val vm = DetailViewModel(repo, "b")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.beer.collect() }
        assertEquals("Punk IPA", vm.beer.value?.name)
    }

    @Test
    fun `toggles flip favourite and buy again`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", favourite = false, buyAgain = false))
        val vm = DetailViewModel(repo, "a")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.beer.collect() }
        vm.toggleFavourite()
        assertTrue(repo.getBeer("a")!!.favourite)
        vm.toggleBuyAgain()
        assertTrue(repo.getBeer("a")!!.buyAgain)
    }

    @Test
    fun `delete removes the beer and invokes the callback`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a"))
        val vm = DetailViewModel(repo, "a")
        var deleted = false
        vm.delete { deleted = true }
        assertTrue(deleted)
        assertEquals(0, repo.observeBeers().first().size)
    }
}
