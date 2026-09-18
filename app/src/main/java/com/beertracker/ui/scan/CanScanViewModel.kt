package com.beertracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CanTextParser
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.CatalogTextMatcher
import com.beertracker.ui.components.CatalogRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CanScanUiState(
    /** True once any word or name-like line has been read, even if nothing matched. */
    val hasText: Boolean = false,
    /** The most name-like line read so far, for the "Add manually" path. */
    val guessedName: String? = null,
    /** Up to five catalog candidates, best first, joined with the user's logged beers. */
    val matches: List<CatalogRow> = emptyList(),
)

/**
 * Drives the can scan screen. Words read from the camera accumulate across
 * frames, so turning the can slowly adds the text printed around it and a
 * blurry frame contributes nothing. Matching runs on [matchDispatcher]
 * (a background dispatcher in the app, the test dispatcher in tests).
 */
class CanScanViewModel(
    catalogRepository: CatalogRepository,
    beerRepository: BeerRepository,
    matchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private data class Reading(
        val tokens: Set<String> = emptySet(),
        val guessedName: String? = null,
    ) {
        val hasText: Boolean get() = tokens.isNotEmpty() || guessedName != null
    }

    private val reading = MutableStateFlow(Reading())

    val uiState: StateFlow<CanScanUiState> = combine(
        catalogRepository.observeProducts().map { products -> CatalogTextMatcher(products) },
        beerRepository.observeBeers(),
        reading,
    ) { matcher, beers, current ->
        // First match wins if the same article was somehow logged twice.
        val loggedByArticle = buildMap {
            for (beer in beers) {
                val number = beer.catalogArticleNumber ?: continue
                putIfAbsent(number, beer)
            }
        }
        val rows = if (current.tokens.isEmpty()) {
            emptyList()
        } else {
            matcher.match(current.tokens).map { match ->
                val logged = loggedByArticle[match.product.articleNumber]
                CatalogRow(
                    product = match.product,
                    triedBeerId = logged?.id,
                    grade = logged?.grade,
                    tried = logged?.tried ?: false,
                )
            }
        }
        CanScanUiState(
            hasText = current.hasText,
            guessedName = current.guessedName,
            matches = rows,
        )
    }
        .flowOn(matchDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CanScanUiState())

    /**
     * Feed of raw recognized text from the camera analyzer. Safe to call on
     * every frame: a frame that brings no new word and no longer name-like
     * line leaves the state object untouched, so nothing is recomputed.
     */
    fun onTextDetected(rawText: String) {
        val tokens = CatalogTextMatcher.tokenize(rawText)
        val guess = CanTextParser.guessName(rawText)
        reading.update { current ->
            val mergedTokens = if (tokens.all { it in current.tokens }) current.tokens else current.tokens + tokens
            val currentGuess = current.guessedName
            val bestGuess = when {
                guess == null -> currentGuess
                currentGuess == null -> guess
                guess.count(Char::isLetter) > currentGuess.count(Char::isLetter) -> guess
                else -> currentGuess
            }
            if (mergedTokens === current.tokens && bestGuess == currentGuess) {
                current
            } else {
                Reading(mergedTokens, bestGuess)
            }
        }
    }

    /** Forgets everything read so far so the next frames start from a clean slate. */
    fun startOver() {
        reading.value = Reading()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                CanScanViewModel(app.container.catalogRepository, app.container.beerRepository)
            }
        }
    }
}
