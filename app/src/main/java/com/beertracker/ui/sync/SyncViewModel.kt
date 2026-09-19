package com.beertracker.ui.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.data.CellarSyncEngine
import com.beertracker.domain.SyncException
import com.beertracker.domain.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SyncError { INVALID_CODE, UNKNOWN_CODE, OFFLINE, UNAVAILABLE, FAILED }

data class SyncUiState(
    val status: SyncStatus = SyncStatus.NotPaired,
    val codeInput: String = "",
    val working: Boolean = false,
    val error: SyncError? = null,
)

class SyncViewModel(private val engine: CellarSyncEngine) : ViewModel() {

    private val codeInput = MutableStateFlow("")
    private val working = MutableStateFlow(false)
    private val error = MutableStateFlow<SyncError?>(null)

    val uiState: StateFlow<SyncUiState> =
        combine(engine.status, codeInput, working, error) { status, code, busy, problem ->
            SyncUiState(status = status, codeInput = code, working = busy, error = problem)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SyncUiState(status = engine.status.value),
        )

    /** Upper-cased as typed; the code alphabet has no lower case. Clears any error. */
    fun setCodeInput(value: String) {
        codeInput.value = value.uppercase()
        error.value = null
    }

    fun createCellar() = perform { engine.createCellar() }

    fun join() = perform {
        engine.joinCellar(codeInput.value)
        codeInput.value = ""
    }

    fun stopSyncing() {
        engine.stopSyncing()
        error.value = null
    }

    fun dismissError() {
        error.value = null
    }

    private fun perform(action: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        error.value = null
        viewModelScope.launch {
            try {
                action()
            } catch (problem: SyncException) {
                error.value = problem.toSyncError()
            } catch (problem: Exception) {
                if (problem is CancellationException) throw problem
                Log.w(TAG, "Sync action failed unexpectedly", problem)
                error.value = SyncError.FAILED
            } finally {
                working.value = false
            }
        }
    }

    companion object {
        private const val TAG = "SyncViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                SyncViewModel(app.container.syncEngine)
            }
        }
    }
}

private fun SyncException.toSyncError(): SyncError = when (this) {
    is SyncException.Unavailable -> SyncError.UNAVAILABLE
    is SyncException.Offline -> SyncError.OFFLINE
    is SyncException.InvalidCode -> SyncError.INVALID_CODE
    is SyncException.UnknownCode -> SyncError.UNKNOWN_CODE
    is SyncException.Failed -> SyncError.FAILED
}
