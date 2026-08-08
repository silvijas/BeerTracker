package com.beertracker.ui

import android.util.Log
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
import kotlinx.coroutines.CancellationException
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
            _refreshState.value = try {
                CatalogRefreshUiState.Done(catalogRefresher.refresh())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Catalog refresh failed unexpectedly, keeping the previous catalog", error)
                CatalogRefreshUiState.Done(
                    RefreshResult.Failure("Could not reach the Systembolaget catalog"),
                )
            }
        }
    }

    /** Called after the result snackbar has been shown. */
    fun acknowledgeResult() {
        if (_refreshState.value is CatalogRefreshUiState.Done) {
            _refreshState.value = CatalogRefreshUiState.Idle
        }
    }

    companion object {
        private const val TAG = "CatalogRefreshViewModel"

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
