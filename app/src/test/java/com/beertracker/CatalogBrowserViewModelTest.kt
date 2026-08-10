package com.beertracker

import com.beertracker.ui.catalog.CatalogBrowserEmptyState
import com.beertracker.ui.catalog.CatalogBrowserUiState
import com.beertracker.ui.catalog.CatalogBrowserViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CatalogBrowserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun catalogWithTwoBeers() = FakeCatalogRepository().apply {
        add(catalogProduct())
        add(
            catalogProduct(
                articleNumber = "1000501",
                articleNumberShort = "10005",
                name = "Second Beer",
                brewery = "Other Brewery",
            ),
        )
    }

    @Test
    fun `rows join the catalog with logged beers by article number`() = runTest {
        val beers = FakeBeerRepository()
        beers.addBeer(beer(id = "b1", grade = 4).copy(catalogArticleNumber = "1324515"))
        val vm = CatalogBrowserViewModel(catalogWithTwoBeers(), beers)

        val content = vm.uiState.filterIsInstance<CatalogBrowserUiState.Content>().first()

        assertEquals(2, content.rows.size)
        val logged = content.rows.first { it.product.articleNumber == "1324515" }
        assertEquals("b1", logged.triedBeerId)
        assertEquals(4, logged.grade)
        assertEquals(true, logged.tried)
        val unlogged = content.rows.first { it.product.articleNumber == "1000501" }
        assertNull(unlogged.triedBeerId)
        assertNull(content.emptyState)
    }

    @Test
    fun `the query narrows rows and reports no results`() = runTest {
        val vm = CatalogBrowserViewModel(catalogWithTwoBeers(), FakeBeerRepository())

        vm.setQuery("second")
        val narrowed = vm.uiState.filterIsInstance<CatalogBrowserUiState.Content>()
            .first { it.query == "second" }
        assertEquals(listOf("Second Beer"), narrowed.rows.map { it.product.name })

        vm.setQuery("no such beer")
        val empty = vm.uiState.filterIsInstance<CatalogBrowserUiState.Content>()
            .first { it.query == "no such beer" }
        assertEquals(CatalogBrowserEmptyState.NO_RESULTS, empty.emptyState)
    }

    @Test
    fun `an empty catalog reports the empty state`() = runTest {
        val vm = CatalogBrowserViewModel(FakeCatalogRepository(), FakeBeerRepository())
        val content = vm.uiState.filterIsInstance<CatalogBrowserUiState.Content>().first()
        assertEquals(CatalogBrowserEmptyState.EMPTY_CATALOG, content.emptyState)
        assertEquals(0, content.rows.size)
    }

    @Test
    fun `the first logged beer wins when two share an article number`() = runTest {
        val beers = FakeBeerRepository()
        beers.addBeer(beer(id = "first", grade = 4).copy(catalogArticleNumber = "1324515"))
        beers.addBeer(beer(id = "later", grade = 5).copy(catalogArticleNumber = "1324515"))
        val vm = CatalogBrowserViewModel(catalogWithTwoBeers(), beers)

        val content = vm.uiState.filterIsInstance<CatalogBrowserUiState.Content>().first()
        val row = content.rows.first { it.product.articleNumber == "1324515" }
        assertEquals("first", row.triedBeerId)
        assertFalse(content.rows.any { it.triedBeerId == "later" })
    }
}
