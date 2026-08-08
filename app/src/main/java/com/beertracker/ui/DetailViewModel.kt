package com.beertracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Content(val beer: TriedBeer) : DetailUiState
    data object NotFound : DetailUiState
    data class Error(val message: String) : DetailUiState
}

class DetailViewModel(
    private val repository: BeerRepository,
    private val beerId: String,
) : ViewModel() {

    val uiState: StateFlow<DetailUiState> = repository.observeBeers()
        .map<List<TriedBeer>, DetailUiState> { list ->
            list.find { it.id == beerId }
                ?.let(DetailUiState::Content)
                ?: DetailUiState.NotFound
        }
        .catch { emit(DetailUiState.Error("Could not load beer")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    val beer: StateFlow<TriedBeer?> = uiState
        .map { state -> (state as? DetailUiState.Content)?.beer }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavourite() {
        viewModelScope.launch {
            val current = repository.getBeer(beerId) ?: return@launch
            repository.updateBeer(current.copy(favourite = !current.favourite))
        }
    }

    fun toggleBuyAgain() {
        viewModelScope.launch {
            val current = repository.getBeer(beerId) ?: return@launch
            repository.updateBeer(current.copy(buyAgain = !current.buyAgain))
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteBeer(beerId)
            onDeleted()
        }
    }

    companion object {
        fun factory(beerId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                DetailViewModel(app.container.beerRepository, beerId)
            }
        }
    }
}
