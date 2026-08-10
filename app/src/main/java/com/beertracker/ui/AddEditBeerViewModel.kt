package com.beertracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CatalogBrowseLogic
import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.Pairing
import com.beertracker.domain.Presets
import com.beertracker.domain.TriedBeer
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EditLoadState {
    data object Loading : EditLoadState
    data object Content : EditLoadState
    data object NotFound : EditLoadState
    data class Error(val message: String) : EditLoadState
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Saved : SaveState
    data class Error(val message: String) : SaveState
}

data class BeerFormState(
    val id: String? = null,
    val name: String = "",
    val brewery: String = "",
    val type: String = "",
    val alcoholPercent: String = "",
    val volumeMl: String = "",
    val price: String = "",
    val grade: Int? = null,
    val tried: Boolean = false,
    val note: String = "",
    val aftertaste: String = "",
    val pairings: Set<String> = emptySet(),
    val customPairing: String = "",
    val buyAgain: Boolean = false,
    val favourite: Boolean = false,
    val catalogArticleNumber: String? = null,
    val imageUrl: String? = null,
    val nameError: Boolean = false,
    val gradeError: Boolean = false,
    val alcoholError: Boolean = false,
    val volumeError: Boolean = false,
    val priceError: Boolean = false,
    val loadState: EditLoadState = EditLoadState.Content,
    val saveState: SaveState = SaveState.Idle,
    val hasUnsavedChanges: Boolean = false,
    val saved: Boolean = false,
)

class AddEditBeerViewModel(
    private val repository: BeerRepository,
    private val catalogRepository: CatalogRepository? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _form = MutableStateFlow(BeerFormState())
    val form: StateFlow<BeerFormState> = _form.asStateFlow()

    val typeOptions: StateFlow<List<String>> = repository.observeBeers()
        .map { beers ->
            (Presets.beerTypes + beers.map { it.type }).filter { it.isNotBlank() }.distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Presets.beerTypes)

    /**
     * The whole pairing vocabulary, always, followed by any value a saved
     * beer carries that is not in it, so a pairing typed once stays reusable.
     */
    val pairingOptions: StateFlow<List<String>> = repository.observeBeers()
        .map { beers ->
            val custom = beers
                .flatMap { it.goesWellWith }
                .filter { Pairing.fromLabel(it) == null }
                .distinct()
            Pairing.entries.map { it.label } + custom
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Pairing.entries.map { it.label },
        )

    private var existing: TriedBeer? = null
    private var loadedBeerId: String? = null
    private var prefilledArticle: String? = null
    private var baseline = BeerFormState()

    /**
     * The name of the last catalog product put into the form, from a picked
     * suggestion or a scan prefill. While the name field still holds exactly
     * this text the suggestion list stays hidden, so a just-filled form does
     * not immediately suggest the same beer back.
     */
    private val appliedCatalogName = MutableStateFlow<String?>(null)

    val catalogSuggestions: StateFlow<List<CatalogProduct>> = combine(
        _form,
        catalogRepository?.observeProducts() ?: flowOf(emptyList()),
        appliedCatalogName,
    ) { form, products, appliedName ->
        val query = form.name.trim()
        when {
            form.id != null || loadedBeerId != null -> emptyList()
            query.length < 2 -> emptyList()
            query == appliedName -> emptyList()
            else -> CatalogBrowseLogic.filterAndSort(products, query).take(8)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(beerId: String) {
        if (loadedBeerId == beerId) return
        loadedBeerId = beerId
        existing = null
        _form.update {
            it.copy(
                loadState = EditLoadState.Loading,
                saveState = SaveState.Idle,
                saved = false,
                hasUnsavedChanges = false,
            )
        }
        viewModelScope.launch {
            try {
                val loaded = repository.getBeer(beerId)
                if (loaded == null) {
                    _form.update {
                        it.copy(loadState = EditLoadState.NotFound, hasUnsavedChanges = false)
                    }
                    return@launch
                }
                existing = loaded
                val loadedForm = BeerFormState(
                    id = loaded.id,
                    name = loaded.name,
                    brewery = loaded.brewery,
                    type = loaded.type,
                    alcoholPercent = loaded.alcoholPercent?.toString() ?: "",
                    volumeMl = loaded.volumeMl?.toString() ?: "",
                    price = loaded.price?.toString() ?: "",
                    grade = loaded.grade,
                    tried = loaded.tried,
                    note = loaded.note,
                    aftertaste = loaded.aftertaste,
                    pairings = loaded.goesWellWith.toSet(),
                    catalogArticleNumber = loaded.catalogArticleNumber,
                    imageUrl = loaded.imageUrl,
                    buyAgain = loaded.buyAgain,
                    favourite = loaded.favourite,
                    loadState = EditLoadState.Content,
                )
                baseline = loadedForm
                _form.value = loadedForm
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _form.update {
                    it.copy(
                        loadState = EditLoadState.Error("Could not load beer"),
                        hasUnsavedChanges = false,
                    )
                }
            }
        }
    }

    /**
     * Fills an empty add form from a catalog product. The product's fields,
     * article number, and display image URL are copied onto the form, so
     * saving gives the user's beer its own copies; later catalog refreshes
     * never change a saved beer. Runs at most once per article number, so a
     * configuration change cannot overwrite the user's edits. Unknown
     * numbers leave the form as it was.
     */
    fun prefillFromCatalog(articleNumber: String) {
        val catalog = catalogRepository ?: return
        if (loadedBeerId != null) return
        if (prefilledArticle == articleNumber) return
        prefilledArticle = articleNumber
        viewModelScope.launch {
            try {
                val product = catalog.findByArticleNumber(articleNumber) ?: return@launch
                if (loadedBeerId != null) return@launch
                appliedCatalogName.value = product.name
                val prefilled = formFilledFrom(product)
                _form.value = prefilled.copy(
                    hasUnsavedChanges = prefilled.formContent() != baseline.formContent(),
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // A failed lookup leaves the empty manual form usable.
            }
        }
    }

    private fun formFilledFrom(product: CatalogProduct) = BeerFormState(
        name = product.name,
        brewery = product.brewery,
        type = product.type,
        alcoholPercent = product.alcoholPercent?.toString() ?: "",
        volumeMl = product.volumeMl?.toString() ?: "",
        price = product.price?.toString() ?: "",
        catalogArticleNumber = product.articleNumber,
        imageUrl = product.displayImageUrl,
        pairings = product.pairings.toSet(),
    )

    /**
     * Fills the add form from a catalog product picked in the suggestion
     * list. Same copy semantics as [prefillFromCatalog]: the form gets its
     * own copies of every field, so catalog refreshes never touch the
     * saved beer. Ignored while editing an existing beer.
     */
    fun applyCatalogProduct(product: CatalogProduct) {
        if (loadedBeerId != null) return
        appliedCatalogName.value = product.name
        val filled = formFilledFrom(product)
        _form.value = filled.copy(
            hasUnsavedChanges = filled.formContent() != baseline.formContent(),
        )
    }

    fun update(transform: (BeerFormState) -> BeerFormState) = _form.update { current ->
        transform(current)
            .withNumericValidation()
            .let { updated ->
                updated.copy(hasUnsavedChanges = updated.formContent() != baseline.formContent())
            }
    }

    /** Picking a grade marks the beer tried. Passing null clears the grade and keeps tried. */
    fun setGrade(value: Int?) = update {
        it.copy(grade = value, tried = it.tried || value != null, gradeError = false)
    }

    /** Turning tried off clears the grade, which keeps the domain invariant satisfied. */
    fun setTried(value: Boolean) = update {
        if (value) {
            it.copy(tried = true)
        } else {
            it.copy(tried = false, grade = null, gradeError = false)
        }
    }

    fun save() {
        if (_form.value.loadState != EditLoadState.Content ||
            _form.value.saveState == SaveState.Saving
        ) {
            return
        }
        val f = _form.value.withNumericValidation()
        _form.value = f
        if (f.name.isBlank()) {
            _form.update { it.copy(nameError = true) }
            return
        }
        if (f.grade != null && f.grade !in 1..5) {
            _form.update { it.copy(gradeError = true) }
            return
        }
        if (f.alcoholError || f.volumeError || f.priceError) return
        val custom = f.customPairing.trim()
        val pairings = buildList {
            addAll(f.pairings)
            if (custom.isNotEmpty() && custom !in f.pairings) add(custom)
        }
        val beer = TriedBeer(
            id = f.id ?: UUID.randomUUID().toString(),
            name = f.name.trim(),
            brewery = f.brewery.trim(),
            type = f.type.trim(),
            alcoholPercent = f.alcoholPercent.replace(',', '.').toDoubleOrNull(),
            volumeMl = f.volumeMl.trim().toIntOrNull(),
            price = f.price.replace(',', '.').toDoubleOrNull(),
            grade = f.grade,
            tried = f.tried || f.grade != null,
            note = f.note.trim(),
            aftertaste = f.aftertaste.trim(),
            goesWellWith = pairings,
            buyAgain = f.buyAgain,
            favourite = f.favourite,
            dateAdded = existing?.dateAdded ?: clock(),
            catalogArticleNumber = f.catalogArticleNumber,
            addedBy = existing?.addedBy,
            imageUrl = f.imageUrl,
        )
        _form.update { it.copy(saveState = SaveState.Saving, saved = false) }
        viewModelScope.launch {
            try {
                if (existing == null) repository.addBeer(beer) else repository.updateBeer(beer)
                existing = beer
                val savedForm = f.copy(
                    id = beer.id,
                    tried = beer.tried,
                    pairings = pairings.toSet(),
                    customPairing = "",
                    nameError = false,
                    gradeError = false,
                    alcoholError = false,
                    volumeError = false,
                    priceError = false,
                    saveState = SaveState.Saved,
                    hasUnsavedChanges = false,
                    saved = true,
                )
                baseline = savedForm
                _form.update { current ->
                    if (current.formContent() == f.formContent()) {
                        savedForm
                    } else {
                        val currentWithId = current.copy(id = beer.id)
                        currentWithId.copy(
                            saveState = SaveState.Saved,
                            hasUnsavedChanges =
                                currentWithId.formContent() != baseline.formContent(),
                            saved = false,
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _form.update {
                    it.copy(
                        saveState = SaveState.Error("Could not save beer"),
                        saved = false,
                    )
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                AddEditBeerViewModel(app.container.beerRepository, app.container.catalogRepository)
            }
        }
    }
}

private fun BeerFormState.withNumericValidation(): BeerFormState {
    val alcohol = alcoholPercent.trim().replace(',', '.')
    val alcoholValue = alcohol.toDoubleOrNull()
    val volume = volumeMl.trim()
    val volumeValue = volume.toIntOrNull()
    val normalizedPrice = price.trim().replace(',', '.')
    val priceValue = normalizedPrice.toDoubleOrNull()
    return copy(
        alcoholError = alcohol.isNotEmpty() &&
            (alcoholValue == null || !alcoholValue.isFinite() || alcoholValue !in 0.0..100.0),
        volumeError = volume.isNotEmpty() && (volumeValue == null || volumeValue <= 0),
        priceError = normalizedPrice.isNotEmpty() &&
            (priceValue == null || !priceValue.isFinite() || priceValue < 0.0),
    )
}

private fun BeerFormState.formContent(): BeerFormState = copy(
    nameError = false,
    gradeError = false,
    alcoholError = false,
    volumeError = false,
    priceError = false,
    loadState = EditLoadState.Content,
    saveState = SaveState.Idle,
    hasUnsavedChanges = false,
    saved = false,
)
