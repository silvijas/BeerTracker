package com.beertracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.ArticleNumberParser
import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Searching : ScanUiState
    data class Found(val product: CatalogProduct) : ScanUiState
    data class NotFound(val number: String) : ScanUiState
}

class ScanViewModel(private val catalogRepository: CatalogRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Numbers already checked against the catalog, so a stream of identical
     * camera frames costs one lookup, not one per frame. */
    private val checkedNumbers = mutableSetOf<String>()

    /**
     * Feed of raw recognized text from the camera analyzer. Safe to call on
     * every frame; the first confirmed catalog hit wins and later frames are
     * ignored. Camera misses are silent (the next frame may be sharper), so
     * this never produces NotFound.
     */
    fun onTextDetected(rawText: String) {
        if (_uiState.value is ScanUiState.Found) return
        val newCandidates = ArticleNumberParser.extractCandidates(rawText)
            .filter(checkedNumbers::add)
        if (newCandidates.isEmpty()) return
        viewModelScope.launch {
            for (number in newCandidates) {
                if (_uiState.value is ScanUiState.Found) return@launch
                val product = catalogRepository.findByArticleNumber(number)
                // Re-checked immediately before the write: another lookup
                // (a later candidate in this same sweep, or a concurrent
                // manual lookup) may have already settled on Found while
                // this suspending call was in flight.
                if (product != null && _uiState.value !is ScanUiState.Found) {
                    _uiState.value = ScanUiState.Found(product)
                    return@launch
                }
            }
        }
    }

    /** The typed fallback path. Unlike the camera feed, a miss is reported. */
    fun onManualLookup(input: String) {
        val number = input.trim()
        if (number.isEmpty() ||
            _uiState.value is ScanUiState.Found ||
            _uiState.value == ScanUiState.Searching
        ) {
            return
        }
        _uiState.value = ScanUiState.Searching
        viewModelScope.launch {
            val product = catalogRepository.findByArticleNumber(number)
            // Re-checked immediately before the write: a concurrent camera
            // detection may have already settled on Found while this
            // suspending call was in flight, and that first hit must stand.
            if (_uiState.value is ScanUiState.Found) return@launch
            _uiState.value = if (product != null) {
                ScanUiState.Found(product)
            } else {
                ScanUiState.NotFound(number)
            }
        }
    }

    /** Clears a NotFound answer so scanning can continue from a clean slate. */
    fun scanAgain() {
        checkedNumbers.clear()
        _uiState.value = ScanUiState.Idle
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                ScanViewModel(app.container.catalogRepository)
            }
        }
    }
}
