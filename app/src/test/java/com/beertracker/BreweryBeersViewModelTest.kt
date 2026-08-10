package com.beertracker

import com.beertracker.domain.BrewerySort
import com.beertracker.ui.brewery.BreweryBeersEmptyState
import com.beertracker.ui.brewery.BreweryBeersUiState
import com.beertracker.ui.brewery.BreweryBeersViewModel
import com.beertracker.ui.brewery.BreweryTriedFilter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BreweryBeersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun omnipolloCatalog() = FakeCatalogRepository().apply {
        add(catalogProduct(brewery = "Omnipollo"))
        add(
            catalogProduct(
                articleNumber = "2000501",
                articleNumberShort = "20005",
                name = "Omnipollo Bianca",
                brewery = "omnipollo",
                type = "Sour",
            ),
        )
        add(
            catalogProduct(
                articleNumber = "3000501",
                articleNumberShort = "30005",
                name = "Other Brewery Lager",
                brewery = "Other Brewery",
                type = "Lager",
            ),
        )
    }

    @Test
    fun `rows are limited to the matching brewery regardless of case`() = runTest {
        val vm = BreweryBeersViewModel(omnipolloCatalog(), FakeBeerRepository(), "Omnipollo")

        val content = vm.uiState.filterIsInstance<BreweryBeersUiState.Content>().first()

        assertEquals(
            setOf("Omnipollo Prodigal Pale Ale", "Omnipollo Bianca"),
            content.rows.map { it.product.name }.toSet(),
        )
        assertFalse(content.rows.any { it.product.name == "Other Brewery Lager" })
    }

    @Test
    fun `rows cross reference tried beers by article number`() = runTest {
        val beers = FakeBeerRepository()
        beers.addBeer(beer(id = "b1").copy(catalogArticleNumber = "1324515"))
        val vm = BreweryBeersViewModel(omnipolloCatalog(), beers, "Omnipollo")

        val content = vm.uiState.filterIsInstance<BreweryBeersUiState.Content>().first()

        val tried = content.rows.first { it.product.articleNumber == "1324515" }
        assertEquals("b1", tried.triedBeerId)
        assertTrue(tried.tried)
        val untried = content.rows.first { it.product.articleNumber == "2000501" }
        assertFalse(untried.tried)
    }

    @Test
    fun `sort changes row order between name and type`() = runTest {
        // Chosen so name order and type order disagree: by name, Alpha comes
        // first; by type, Beta's "Ale" collates before Alpha's "Stout".
        val catalog = FakeCatalogRepository().apply {
            add(
                catalogProduct(
                    articleNumber = "1", articleNumberShort = "1",
                    name = "Alpha Stout", brewery = "Zeta Bryggeri", type = "Stout",
                ),
            )
            add(
                catalogProduct(
                    articleNumber = "2", articleNumberShort = "2",
                    name = "Beta Ale", brewery = "Zeta Bryggeri", type = "Ale",
                ),
            )
        }
        val vm = BreweryBeersViewModel(catalog, FakeBeerRepository(), "Zeta Bryggeri")

        val byName = vm.uiState.filterIsInstance<BreweryBeersUiState.Content>()
            .first { it.sort == BrewerySort.NAME }
        assertEquals(listOf("Alpha Stout", "Beta Ale"), byName.rows.map { it.product.name })

        vm.setSort(BrewerySort.TYPE)
        val byType = vm.uiState.filterIsInstance<BreweryBeersUiState.Content>()
            .first { it.sort == BrewerySort.TYPE }
        assertEquals(listOf("Beta Ale", "Alpha Stout"), byType.rows.map { it.product.name })
    }

    @Test
    fun `the tried filter hides the other group and reports when everything is hidden`() = runTest {
        val beers = FakeBeerRepository()
        beers.addBeer(beer(id = "b1").copy(catalogArticleNumber = "1324515"))
        val vm = BreweryBeersViewModel(omnipolloCatalog(), beers, "Omnipollo")

        vm.setFilter(BreweryTriedFilter.TRIED)
        val triedOnly = vm.uiState.filterIsInstance<BreweryBeersUiState.Content>()
            .first { it.filter == BreweryTriedFilter.TRIED }
        assertEquals(listOf("1324515"), triedOnly.rows.map { it.product.articleNumber })
        assertEquals(null, triedOnly.emptyState)

        vm.setFilter(BreweryTriedFilter.NOT_TRIED)
        val untriedOnly = vm.uiState.filterIsInstance<BreweryBeersUiState.Content>()
            .first { it.filter == BreweryTriedFilter.NOT_TRIED }
        assertEquals(listOf("2000501"), untriedOnly.rows.map { it.product.articleNumber })

        val allTried = FakeBeerRepository()
        allTried.addBeer(beer(id = "b1").copy(catalogArticleNumber = "1324515"))
        allTried.addBeer(beer(id = "b2").copy(catalogArticleNumber = "2000501"))
        val vmAllTried = BreweryBeersViewModel(omnipolloCatalog(), allTried, "Omnipollo")
        vmAllTried.setFilter(BreweryTriedFilter.NOT_TRIED)
        val nothingLeft = vmAllTried.uiState.filterIsInstance<BreweryBeersUiState.Content>()
            .first { it.filter == BreweryTriedFilter.NOT_TRIED }
        assertEquals(BreweryBeersEmptyState.FILTERED_EMPTY, nothingLeft.emptyState)
    }

    @Test
    fun `no catalog matches for the brewery reports the empty state`() = runTest {
        val vm = BreweryBeersViewModel(omnipolloCatalog(), FakeBeerRepository(), "Nobody Brews This")

        val content = vm.uiState.filterIsInstance<BreweryBeersUiState.Content>().first()

        assertEquals(0, content.rows.size)
        assertEquals(BreweryBeersEmptyState.NO_CATALOG_MATCHES, content.emptyState)
    }
}
