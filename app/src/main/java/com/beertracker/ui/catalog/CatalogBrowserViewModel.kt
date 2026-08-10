package com.beertracker.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CatalogBrowseLogic
import com.beertracker.domain.CatalogRepository
import com.beertracker.ui.components.CatalogRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class CatalogBrowserEmptyState { EMPTY_CATALOG, NO_RESULTS }

sealed interface CatalogBrowserUiState {
    data object Loading : CatalogBrowserUiState
    data class Content(
        val rows: List<CatalogRow>,
        val query: String,
        val emptyState: CatalogBrowserEmptyState?,
    ) : CatalogBrowserUiState
}

class CatalogBrowserViewModel(
    catalogRepository: CatalogRepository,
    beerRepository: BeerRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<CatalogBrowserUiState> = combine(
        catalogRepository.observeProducts(),
        beerRepository.observeBeers(),
        query,
    ) { products, beers, q ->
        // First match wins if the same article was somehow logged twice.
        val loggedByArticle = buildMap {
            for (beer in beers) {
                val number = beer.catalogArticleNumber ?: continue
                putIfAbsent(number, beer)
            }
        }
        val visible = CatalogBrowseLogic.filterAndSort(products, q)
        CatalogBrowserUiState.Content(
            rows = visible.map { product ->
                val logged = loggedByArticle[product.articleNumber]
                CatalogRow(
                    product = product,
                    triedBeerId = logged?.id,
                    grade = logged?.grade,
                    tried = logged?.tried ?: false,
                )
            },
            query = q,
            emptyState = when {
                products.isEmpty() -> CatalogBrowserEmptyState.EMPTY_CATALOG
                visible.isEmpty() -> CatalogBrowserEmptyState.NO_RESULTS
                else -> null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogBrowserUiState.Loading)

    fun setQuery(value: String) {
        query.value = value
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                CatalogBrowserViewModel(
                    app.container.catalogRepository,
                    app.container.beerRepository,
                )
            }
        }
    }
}
