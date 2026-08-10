package com.beertracker

import com.beertracker.domain.BeerSort
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import com.beertracker.ui.OverviewEmptyState
import com.beertracker.ui.OverviewUiState
import com.beertracker.ui.OverviewViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun kotlinx.coroutines.test.TestScope.collecting(vm: OverviewViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
    }

    @Test
    fun `startup stays loading until the repository emits`() = runTest {
        val emissions = MutableSharedFlow<List<TriedBeer>>()
        val vm = OverviewViewModel(ObservationRepository { emissions })
        collecting(vm)

        assertEquals(OverviewUiState.Loading, vm.uiState.value)

        emissions.emit(emptyList())

        assertEquals(
            OverviewEmptyState.EMPTY_CELLAR,
            (vm.uiState.value as OverviewUiState.Content).emptyState,
        )
    }

    @Test
    fun `repository observation failure exposes error state`() = runTest {
        val vm = OverviewViewModel(
            ObservationRepository {
                flow { throw IllegalStateException("Observation failed") }
            },
        )
        collecting(vm)

        assertEquals(OverviewUiState.Error, vm.uiState.value)
    }

    @Test
    fun `try again resubscribes and exposes successful content`() = runTest {
        var subscriptions = 0
        val vm = OverviewViewModel(
            ObservationRepository {
                subscriptions += 1
                if (subscriptions == 1) {
                    flow { throw IllegalStateException("Observation failed") }
                } else {
                    flowOf(listOf(beer(id = "recovered")))
                }
            },
        )
        collecting(vm)
        assertEquals(OverviewUiState.Error, vm.uiState.value)

        vm.tryAgain()

        val content = vm.uiState.value as OverviewUiState.Content
        assertEquals(listOf("recovered"), content.beers.map { it.id })
    }

    @Test
    fun `default sort is grade descending`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "low", grade = 2))
        repo.addBeer(beer(id = "high", grade = 5))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        assertEquals(listOf("high", "low"), vm.uiState.value.beers.map { it.id })
    }

    @Test
    fun `default sort puts ungraded beers last`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "untried", grade = null, tried = false, dateAdded = 9L))
        repo.addBeer(beer(id = "graded", grade = 3, dateAdded = 1L))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        assertEquals(listOf("graded", "untried"), vm.uiState.value.beers.map { it.id })
    }

    @Test
    fun `query narrows the list`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Falcon"))
        repo.addBeer(beer(id = "b", name = "Punk IPA"))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        vm.setQuery("punk")
        assertEquals(listOf("b"), vm.uiState.value.beers.map { it.id })
    }

    @Test
    fun `type toggle filters and toggles off again`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", type = "Lager"))
        repo.addBeer(beer(id = "b", type = "IPA"))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        vm.toggleType("IPA")
        assertEquals(listOf("b"), vm.uiState.value.beers.map { it.id })
        vm.toggleType("IPA")
        assertEquals(2, vm.uiState.value.beers.size)
    }

    @Test
    fun `not tried toggle filters and toggles off again`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "graded", grade = 4))
        repo.addBeer(beer(id = "untried", grade = null, tried = false))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        vm.toggleNotTriedOnly()
        assertEquals(listOf("untried"), vm.uiState.value.beers.map { it.id })
        assertTrue(vm.uiState.value.filter.notTriedOnly)
        vm.toggleNotTriedOnly()
        assertEquals(2, vm.uiState.value.beers.size)
    }

    @Test
    fun `available types are distinct and sorted`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", type = "Lager"))
        repo.addBeer(beer(id = "b", type = "IPA"))
        repo.addBeer(beer(id = "c", type = "Lager"))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        assertEquals(listOf("IPA", "Lager"), vm.uiState.value.availableTypes)
    }

    @Test
    fun `sort can be changed`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "old", grade = 5, dateAdded = 1L))
        repo.addBeer(beer(id = "new", grade = 2, dateAdded = 2L))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        vm.setSort(BeerSort.DATE_ADDED)
        assertEquals(listOf("new", "old"), vm.uiState.value.beers.map { it.id })
        assertTrue(vm.uiState.value.sort == BeerSort.DATE_ADDED)
    }

    @Test
    fun `empty repository exposes empty cellar state`() = runTest {
        val vm = OverviewViewModel(FakeBeerRepository())
        collecting(vm)

        assertEquals(OverviewEmptyState.EMPTY_CELLAR, vm.uiState.value.emptyState)
    }

    @Test
    fun `filters with no matches expose no results state`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Falcon"))
        val vm = OverviewViewModel(repo)
        collecting(vm)

        vm.setQuery("missing")

        assertEquals(OverviewEmptyState.NO_RESULTS, vm.uiState.value.emptyState)
    }

    @Test
    fun `clear filters restores the full cellar`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Falcon"))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        vm.setQuery("missing")
        vm.toggleFavouritesOnly()
        vm.setSort(BeerSort.DATE_ADDED)

        vm.clearFilters()

        assertEquals(listOf("a"), vm.uiState.value.beers.map { it.id })
        assertEquals("", vm.uiState.value.filter.query)
        assertFalse(vm.uiState.value.filter.favouritesOnly)
        assertEquals(BeerSort.DATE_ADDED, vm.uiState.value.sort)
        assertNull(vm.uiState.value.emptyState)
    }
}

private class ObservationRepository(
    private val observation: () -> Flow<List<TriedBeer>>,
) : BeerRepository {
    override fun observeBeers(): Flow<List<TriedBeer>> = observation()

    override suspend fun getBeer(id: String): TriedBeer? = null

    override suspend fun addBeer(beer: TriedBeer) = Unit

    override suspend fun updateBeer(beer: TriedBeer) = Unit

    override suspend fun deleteBeer(id: String) = Unit
}
