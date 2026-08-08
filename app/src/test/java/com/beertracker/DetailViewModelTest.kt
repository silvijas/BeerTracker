package com.beertracker

import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import com.beertracker.ui.DetailUiState
import com.beertracker.ui.DetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `detail starts in loading state`() {
        val vm = DetailViewModel(FakeBeerRepository(), "a")

        assertEquals(DetailUiState.Loading, vm.uiState.value)
    }

    @Test
    fun `existing beer exposes content state`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Falcon"))
        val vm = DetailViewModel(repo, "a")

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        assertEquals("Falcon", (vm.uiState.value as DetailUiState.Content).beer.name)
    }

    @Test
    fun `missing beer exposes not found state`() = runTest {
        val vm = DetailViewModel(FakeBeerRepository(), "missing")

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        assertEquals(DetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `repository failure exposes detail error state`() = runTest {
        val vm = DetailViewModel(FailingObserveRepository(), "a")

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        assertEquals("Could not load beer", (vm.uiState.value as DetailUiState.Error).message)
    }
}

private class FailingObserveRepository : BeerRepository {
    override fun observeBeers(): Flow<List<TriedBeer>> = flow {
        throw IllegalStateException("database unavailable")
    }

    override suspend fun getBeer(id: String): TriedBeer? = null

    override suspend fun addBeer(beer: TriedBeer) = Unit

    override suspend fun updateBeer(beer: TriedBeer) = Unit

    override suspend fun deleteBeer(id: String) = Unit
}
