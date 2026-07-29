package com.beertracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerFilter
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.BeerSort
import com.beertracker.domain.TriedBeer
import com.beertracker.domain.filterAndSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class OverviewUiState(
    val beers: List<TriedBeer> = emptyList(),
    val filter: BeerFilter = BeerFilter(),
    val sort: BeerSort = BeerSort.GRADE,
    val availableTypes: List<String> = emptyList(),
)

class OverviewViewModel(repository: BeerRepository) : ViewModel() {

    private val filter = MutableStateFlow(BeerFilter())
    private val sort = MutableStateFlow(BeerSort.GRADE)

    val uiState: StateFlow<OverviewUiState> =
        combine(repository.observeBeers(), filter, sort) { beers, f, s ->
            OverviewUiState(
                beers = filterAndSort(beers, f, s),
                filter = f,
                sort = s,
                availableTypes = beers.map { it.type }.filter { it.isNotBlank() }.distinct().sorted(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun toggleBuyAgainOnly() = filter.update { it.copy(buyAgainOnly = !it.buyAgainOnly) }

    fun toggleFavouritesOnly() = filter.update { it.copy(favouritesOnly = !it.favouritesOnly) }

    fun toggleNotTriedOnly() = filter.update { it.copy(notTriedOnly = !it.notTriedOnly) }

    fun toggleType(type: String) = filter.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }

    fun setSort(value: BeerSort) {
        sort.value = value
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                OverviewViewModel(app.container.beerRepository)
            }
        }
    }
}
