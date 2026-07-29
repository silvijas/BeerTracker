package com.beertracker

import com.beertracker.domain.BeerSort
import com.beertracker.ui.OverviewViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OverviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun kotlinx.coroutines.test.TestScope.collecting(vm: OverviewViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
    }

    @Test
    fun `default sort is grade descending`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "low", grade = 6))
        repo.addBeer(beer(id = "high", grade = 10))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        assertEquals(listOf("high", "low"), vm.uiState.value.beers.map { it.id })
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
        repo.addBeer(beer(id = "old", grade = 10, dateAdded = 1L))
        repo.addBeer(beer(id = "new", grade = 5, dateAdded = 2L))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        vm.setSort(BeerSort.DATE_ADDED)
        assertEquals(listOf("new", "old"), vm.uiState.value.beers.map { it.id })
        assertTrue(vm.uiState.value.sort == BeerSort.DATE_ADDED)
    }
}
