package com.beertracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.CatalogRefresher
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.CatalogStatus
import com.beertracker.domain.RefreshResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CatalogRefreshUiState {
    data object Idle : CatalogRefreshUiState
    data object Refreshing : CatalogRefreshUiState
    data class Done(val result: RefreshResult) : CatalogRefreshUiState
}

class CatalogRefreshViewModel(
    catalogRepository: CatalogRepository,
    private val catalogRefresher: CatalogRefresher,
) : ViewModel() {

    private val _refreshState = MutableStateFlow<CatalogRefreshUiState>(CatalogRefreshUiState.Idle)
    val refreshState: StateFlow<CatalogRefreshUiState> = _refreshState.asStateFlow()

    val status: StateFlow<CatalogStatus?> = catalogRepository.observeStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        if (_refreshState.value == CatalogRefreshUiState.Refreshing) return
        _refreshState.value = CatalogRefreshUiState.Refreshing
        viewModelScope.launch {
            _refreshState.value = CatalogRefreshUiState.Done(catalogRefresher.refresh())
        }
    }

    /** Called after the result snackbar has been shown. */
    fun acknowledgeResult() {
        if (_refreshState.value is CatalogRefreshUiState.Done) {
            _refreshState.value = CatalogRefreshUiState.Idle
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                CatalogRefreshViewModel(
                    app.container.catalogRepository,
                    app.container.catalogRefresher,
                )
            }
        }
    }
}
