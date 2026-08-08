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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class OverviewEmptyState {
    EMPTY_CELLAR,
    NO_RESULTS,
}

sealed interface OverviewUiState {
    val beers: List<TriedBeer>
        get() = emptyList()
    val filter: BeerFilter
        get() = BeerFilter()
    val sort: BeerSort
        get() = BeerSort.GRADE
    val availableTypes: List<String>
        get() = emptyList()
    val emptyState: OverviewEmptyState?
        get() = null

    data object Loading : OverviewUiState

    data class Content(
        override val beers: List<TriedBeer>,
        override val filter: BeerFilter,
        override val sort: BeerSort,
        override val availableTypes: List<String>,
        override val emptyState: OverviewEmptyState?,
    ) : OverviewUiState

    data object Error : OverviewUiState
}

class OverviewViewModel(private val repository: BeerRepository) : ViewModel() {

    private val filter = MutableStateFlow(BeerFilter())
    private val sort = MutableStateFlow(BeerSort.GRADE)
    private val retryRequest = MutableStateFlow(0)

    val uiState: StateFlow<OverviewUiState> =
        combine(observeRepository(), filter, sort) { observation, f, s ->
            when (observation) {
                RepositoryObservation.Loading -> OverviewUiState.Loading
                RepositoryObservation.Error -> OverviewUiState.Error
                is RepositoryObservation.Success -> {
                    val visibleBeers = filterAndSort(observation.beers, f, s)
                    OverviewUiState.Content(
                        beers = visibleBeers,
                        filter = f,
                        sort = s,
                        availableTypes = observation.beers
                            .map { it.type }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted(),
                        emptyState = when {
                            observation.beers.isEmpty() -> OverviewEmptyState.EMPTY_CELLAR
                            visibleBeers.isEmpty() -> OverviewEmptyState.NO_RESULTS
                            else -> null
                        },
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRepository() = retryRequest.flatMapLatest {
        repository.observeBeers()
            .map<List<TriedBeer>, RepositoryObservation>(RepositoryObservation::Success)
            .onStart { emit(RepositoryObservation.Loading) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(RepositoryObservation.Error)
            }
    }

    fun tryAgain() = retryRequest.update { it + 1 }

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun toggleBuyAgainOnly() = filter.update { it.copy(buyAgainOnly = !it.buyAgainOnly) }

    fun toggleFavouritesOnly() = filter.update { it.copy(favouritesOnly = !it.favouritesOnly) }

    fun toggleNotTriedOnly() = filter.update { it.copy(notTriedOnly = !it.notTriedOnly) }

    fun toggleType(type: String) = filter.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }

    fun clearFilters() {
        filter.value = BeerFilter()
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

private sealed interface RepositoryObservation {
    data object Loading : RepositoryObservation
    data class Success(val beers: List<TriedBeer>) : RepositoryObservation
    data object Error : RepositoryObservation
}
