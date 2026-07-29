package com.beertracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.Presets
import com.beertracker.domain.TriedBeer
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BeerFormState(
    val id: String? = null,
    val name: String = "",
    val brewery: String = "",
    val type: String = "",
    val alcoholPercent: String = "",
    val volumeMl: String = "",
    val price: String = "",
    val grade: Int = 7,
    val note: String = "",
    val aftertaste: String = "",
    val pairings: Set<String> = emptySet(),
    val customPairing: String = "",
    val buyAgain: Boolean = false,
    val favourite: Boolean = false,
    val nameError: Boolean = false,
    val saved: Boolean = false,
)

class AddEditBeerViewModel(
    private val repository: BeerRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _form = MutableStateFlow(BeerFormState())
    val form: StateFlow<BeerFormState> = _form.asStateFlow()

    val typeOptions: StateFlow<List<String>> = repository.observeBeers()
        .map { beers ->
            (Presets.beerTypes + beers.map { it.type }).filter { it.isNotBlank() }.distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Presets.beerTypes)

    val pairingOptions: StateFlow<List<String>> = repository.observeBeers()
        .map { beers -> (Presets.pairings + beers.flatMap { it.goesWellWith }).distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Presets.pairings)

    private var existing: TriedBeer? = null

    fun load(beerId: String) {
        viewModelScope.launch {
            val loaded = repository.getBeer(beerId) ?: return@launch
            existing = loaded
            _form.value = BeerFormState(
                id = loaded.id,
                name = loaded.name,
                brewery = loaded.brewery,
                type = loaded.type,
                alcoholPercent = loaded.alcoholPercent?.toString() ?: "",
                volumeMl = loaded.volumeMl?.toString() ?: "",
                price = loaded.price?.toString() ?: "",
                grade = loaded.grade,
                note = loaded.note,
                aftertaste = loaded.aftertaste,
                pairings = loaded.goesWellWith.toSet(),
                buyAgain = loaded.buyAgain,
                favourite = loaded.favourite,
            )
        }
    }

    fun update(transform: (BeerFormState) -> BeerFormState) = _form.update(transform)

    fun save() {
        val f = _form.value
        if (f.name.isBlank()) {
            _form.update { it.copy(nameError = true) }
            return
        }
        val pairings = buildList {
            addAll(f.pairings)
            f.customPairing.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
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
            note = f.note.trim(),
            aftertaste = f.aftertaste.trim(),
            goesWellWith = pairings,
            buyAgain = f.buyAgain,
            favourite = f.favourite,
            dateAdded = existing?.dateAdded ?: clock(),
            catalogArticleNumber = existing?.catalogArticleNumber,
            addedBy = existing?.addedBy,
        )
        viewModelScope.launch {
            if (existing == null) repository.addBeer(beer) else repository.updateBeer(beer)
            _form.update { it.copy(saved = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                AddEditBeerViewModel(app.container.beerRepository)
            }
        }
    }
}
