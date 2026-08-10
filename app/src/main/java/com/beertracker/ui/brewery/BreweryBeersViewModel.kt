package com.beertracker.ui.brewery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.BrewerySort
import com.beertracker.domain.CatalogBrowseLogic
import com.beertracker.domain.CatalogRepository
import com.beertracker.ui.components.CatalogRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class BreweryTriedFilter { ALL, TRIED, NOT_TRIED }

enum class BreweryBeersEmptyState { NO_CATALOG_MATCHES, FILTERED_EMPTY }

sealed interface BreweryBeersUiState {
    data object Loading : BreweryBeersUiState
    data class Content(
        val rows: List<CatalogRow>,
        val sort: BrewerySort,
        val filter: BreweryTriedFilter,
        val emptyState: BreweryBeersEmptyState?,
    ) : BreweryBeersUiState
}

class BreweryBeersViewModel(
    catalogRepository: CatalogRepository,
    beerRepository: BeerRepository,
    private val breweryName: String,
) : ViewModel() {

    private val sort = MutableStateFlow(BrewerySort.NAME)
    private val filter = MutableStateFlow(BreweryTriedFilter.ALL)

    val uiState: StateFlow<BreweryBeersUiState> = combine(
        catalogRepository.observeProducts(),
        beerRepository.observeBeers(),
        sort,
        filter,
    ) { products, beers, sortMode, triedFilter ->
        // First match wins if the same article was somehow logged twice.
        val loggedByArticle = buildMap {
            for (beer in beers) {
                val number = beer.catalogArticleNumber ?: continue
                putIfAbsent(number, beer)
            }
        }
        val matching = products.filter { CatalogBrowseLogic.matchesBrewery(it, breweryName) }
        val sorted = CatalogBrowseLogic.sortForBrewery(matching, sortMode)
        val allRows = sorted.map { product ->
            val logged = loggedByArticle[product.articleNumber]
            CatalogRow(
                product = product,
                triedBeerId = logged?.id,
                grade = logged?.grade,
                tried = logged?.tried ?: false,
            )
        }
        val visibleRows = when (triedFilter) {
            BreweryTriedFilter.ALL -> allRows
            BreweryTriedFilter.TRIED -> allRows.filter { it.tried }
            BreweryTriedFilter.NOT_TRIED -> allRows.filter { !it.tried }
        }
        BreweryBeersUiState.Content(
            rows = visibleRows,
            sort = sortMode,
            filter = triedFilter,
            emptyState = when {
                allRows.isEmpty() -> BreweryBeersEmptyState.NO_CATALOG_MATCHES
                visibleRows.isEmpty() -> BreweryBeersEmptyState.FILTERED_EMPTY
                else -> null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BreweryBeersUiState.Loading)

    fun setSort(value: BrewerySort) {
        sort.value = value
    }

    fun setFilter(value: BreweryTriedFilter) {
        filter.value = value
    }

    companion object {
        fun factory(breweryName: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                BreweryBeersViewModel(
                    app.container.catalogRepository,
                    app.container.beerRepository,
                    breweryName,
                )
            }
        }
    }
}
