package com.beertracker

import com.beertracker.ui.scan.CanScanUiState
import com.beertracker.ui.scan.CanScanViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CanScanViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private fun catalogWithSample() = FakeCatalogRepository().apply { add(catalogProduct()) }

    private fun viewModel(
        catalog: FakeCatalogRepository = catalogWithSample(),
        beers: FakeBeerRepository = FakeBeerRepository(),
    ) = CanScanViewModel(catalog, beers, dispatcher)

    @Test
    fun `starts with nothing read and no matches`() = runTest {
        val state = viewModel().uiState.first()

        assertEquals(CanScanUiState(), state)
    }

    @Test
    fun `text naming a catalog beer produces a row for it`() = runTest {
        val vm = viewModel()

        vm.onTextDetected("OMNIPOLLO\nPRODIGAL PALE ALE\n5,2% VOL 330 ML")
        val state = vm.uiState.first { it.matches.isNotEmpty() }

        assertEquals("1324515", state.matches.single().product.articleNumber)
        assertNull(state.matches.single().triedBeerId)
        assertTrue(state.hasText)
    }

    @Test
    fun `words accumulate across frames so a name split over two frames still matches`() = runTest {
        val vm = viewModel()

        vm.onTextDetected("OMNIPOLLO PRODIGAL")
        val afterFirst = vm.uiState.first { it.hasText }
        assertEquals(0, afterFirst.matches.size)

        vm.onTextDetected("PALE ALE")
        val afterSecond = vm.uiState.first { it.matches.isNotEmpty() }
        assertEquals("Omnipollo Prodigal Pale Ale", afterSecond.matches.single().product.name)
    }

    @Test
    fun `a repeated frame does not change the state`() = runTest {
        val vm = viewModel()
        val emissions = mutableListOf<CanScanUiState>()
        backgroundScope.launch { vm.uiState.toList(emissions) }

        vm.onTextDetected("OMNIPOLLO PRODIGAL PALE ALE")
        advanceUntilIdle()
        val countAfterFirst = emissions.size
        vm.onTextDetected("OMNIPOLLO PRODIGAL PALE ALE")
        advanceUntilIdle()

        assertEquals(countAfterFirst, emissions.size)
        assertEquals(1, emissions.last().matches.size)
    }

    @Test
    fun `a logged beer's row carries its id grade and tried flag`() = runTest {
        val beers = FakeBeerRepository()
        beers.addBeer(beer(id = "b1", grade = 4).copy(catalogArticleNumber = "1324515"))
        val vm = viewModel(beers = beers)

        vm.onTextDetected("OMNIPOLLO PRODIGAL PALE ALE")
        val row = vm.uiState.first { it.matches.isNotEmpty() }.matches.single()

        assertEquals("b1", row.triedBeerId)
        assertEquals(4, row.grade)
        assertTrue(row.tried)
    }

    @Test
    fun `the guessed name is the most name-like line seen in any frame`() = runTest {
        val vm = viewModel()

        vm.onTextDetected("OMNIPOLLO\n5,2% VOL")
        assertEquals("OMNIPOLLO", vm.uiState.first { it.hasText }.guessedName)

        vm.onTextDetected("PRODIGAL PALE ALE\n33 CL")
        assertEquals(
            "PRODIGAL PALE ALE",
            vm.uiState.first { it.guessedName == "PRODIGAL PALE ALE" }.guessedName,
        )

        vm.onTextDetected("IPA")
        assertEquals("PRODIGAL PALE ALE", vm.uiState.first { it.hasText }.guessedName)
    }

    @Test
    fun `start over clears matches guess and text`() = runTest {
        val vm = viewModel()
        vm.onTextDetected("OMNIPOLLO PRODIGAL PALE ALE")
        vm.uiState.first { it.matches.isNotEmpty() }

        vm.startOver()
        val state = vm.uiState.first { !it.hasText }

        assertFalse(state.hasText)
        assertNull(state.guessedName)
        assertEquals(0, state.matches.size)
    }

    @Test
    fun `an empty catalog never matches but still records that text was read`() = runTest {
        val vm = viewModel(catalog = FakeCatalogRepository())

        vm.onTextDetected("OMNIPOLLO PRODIGAL PALE ALE")
        val state = vm.uiState.first { it.hasText }

        assertEquals(0, state.matches.size)
        assertEquals("OMNIPOLLO PRODIGAL PALE ALE", state.guessedName)
    }
}
