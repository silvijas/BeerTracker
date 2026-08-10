package com.beertracker

import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import com.beertracker.ui.AddEditBeerViewModel
import com.beertracker.ui.EditLoadState
import com.beertracker.ui.SaveState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddEditBeerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `save with blank name sets error and stores nothing`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "  ") }
        vm.save()
        assertTrue(vm.form.value.nameError)
        assertFalse(vm.form.value.saved)
        assertEquals(0, repo.observeBeers().first().size)
    }

    @Test
    fun `save parses numbers and comma decimals`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo, clock = { 777L })
        vm.update {
            it.copy(
                name = "Punk IPA", brewery = "BrewDog", type = "IPA",
                alcoholPercent = "5,6", volumeMl = "330", price = "29,50",
            )
        }
        vm.setGrade(5)
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals("Punk IPA", saved.name)
        assertEquals(5.6, saved.alcoholPercent!!, 0.001)
        assertEquals(330, saved.volumeMl)
        assertEquals(29.5, saved.price!!, 0.001)
        assertEquals(5, saved.grade)
        assertTrue(saved.tried)
        assertEquals(777L, saved.dateAdded)
        assertTrue(vm.form.value.saved)
    }

    @Test
    fun `blank numeric fields save as null`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Mystery Beer") }
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertNull(saved.alcoholPercent)
        assertNull(saved.volumeMl)
        assertNull(saved.price)
    }

    @Test
    fun `save with no grade stores a beer that is not tried`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Shelf Find Wheat") }
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertNull(saved.grade)
        assertFalse(saved.tried)
        assertTrue(vm.form.value.saved)
    }

    @Test
    fun `selecting a grade marks the beer as tried`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Punk IPA") }
        vm.setGrade(4)
        assertTrue(vm.form.value.tried)
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals(4, saved.grade)
        assertTrue(saved.tried)
    }

    @Test
    fun `the tried toggle can be on with no grade`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Tasted At A Bar") }
        vm.setTried(true)
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertNull(saved.grade)
        assertTrue(saved.tried)
    }

    @Test
    fun `turning tried off clears the grade`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Changed My Mind") }
        vm.setGrade(5)
        vm.setTried(false)
        assertNull(vm.form.value.grade)
        assertFalse(vm.form.value.tried)
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertNull(saved.grade)
        assertFalse(saved.tried)
    }

    @Test
    fun `clearing the grade keeps the beer tried`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "No Score Yet") }
        vm.setGrade(3)
        vm.setGrade(null)
        assertNull(vm.form.value.grade)
        assertTrue(vm.form.value.tried)
    }

    @Test
    fun `an out of range grade sets gradeError and stores nothing`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Bad Grade", grade = 0, tried = true) }
        vm.save()
        assertTrue(vm.form.value.gradeError)
        assertFalse(vm.form.value.saved)
        assertEquals(0, repo.observeBeers().first().size)
    }

    @Test
    fun `custom pairing is appended to selected chips`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update {
            it.copy(name = "X", pairings = setOf("Salmon"), customPairing = "Tacos")
        }
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals(setOf("Salmon", "Tacos"), saved.goesWellWith.toSet())
    }

    @Test
    fun `a successful save clears nameError and the custom pairing field`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.save()
        assertTrue(vm.form.value.nameError)
        vm.update { it.copy(name = "Punk IPA", customPairing = "Tacos") }
        vm.save()
        assertFalse(vm.form.value.nameError)
        assertEquals("", vm.form.value.customPairing)
        assertEquals(setOf("Tacos"), vm.form.value.pairings)
        assertEquals(listOf("Tacos"), repo.observeBeers().first().single().goesWellWith)
    }

    @Test
    fun `editing preserves id and dateAdded`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Old Name", grade = 3, dateAdded = 111L))
        val vm = AddEditBeerViewModel(repo, clock = { 999L })
        vm.load("a")
        vm.update { it.copy(name = "New Name") }
        vm.setGrade(5)
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals("a", saved.id)
        assertEquals("New Name", saved.name)
        assertEquals(5, saved.grade)
        assertEquals(111L, saved.dateAdded)
    }

    @Test
    fun `editing an untried beer loads its state`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Shelf Find", grade = null, tried = false))
        val vm = AddEditBeerViewModel(repo)
        vm.load("a")
        assertNull(vm.form.value.grade)
        assertFalse(vm.form.value.tried)
    }

    @Test
    fun `load again does not reset in progress edits`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Old Name"))
        val vm = AddEditBeerViewModel(repo)
        vm.load("a")
        vm.update { it.copy(name = "Edited") }
        vm.load("a")
        assertEquals("Edited", vm.form.value.name)
    }

    @Test
    fun `edit load sets loading immediately and save is disabled while loading`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = ControlledBeerRepository(
            initialBeers = listOf(beer(id = "a", name = "Old Name")),
            loadGate = gate,
        )
        val vm = AddEditBeerViewModel(repo)

        vm.load("a")
        vm.update { it.copy(name = "New Name") }
        vm.save()

        assertEquals(EditLoadState.Loading, vm.form.value.loadState)
        assertEquals(0, repo.addCalls)
        assertEquals(0, repo.updateCalls)
        gate.complete(Unit)
    }

    @Test
    fun `missing edit exposes not found and never creates a beer`() = runTest {
        val repo = ControlledBeerRepository()
        val vm = AddEditBeerViewModel(repo)

        vm.load("missing")
        vm.update { it.copy(name = "Must Not Be Added") }
        vm.save()

        assertEquals(EditLoadState.NotFound, vm.form.value.loadState)
        assertEquals(0, repo.addCalls)
        assertEquals(0, repo.updateCalls)
        assertTrue(repo.observeBeers().first().isEmpty())
    }

    @Test
    fun `edit load failure exposes a user consumable error`() = runTest {
        val repo = ControlledBeerRepository(loadFailure = IllegalStateException("database unavailable"))
        val vm = AddEditBeerViewModel(repo)

        vm.load("a")

        val state = vm.form.value.loadState as EditLoadState.Error
        assertEquals("Could not load beer", state.message)
    }

    @Test
    fun `alcohol validation is inline and accepts comma decimals`() {
        val vm = AddEditBeerViewModel(FakeBeerRepository())

        vm.update { it.copy(alcoholPercent = "101") }
        assertTrue(vm.form.value.alcoholError)

        vm.update { it.copy(alcoholPercent = "5,6") }
        assertFalse(vm.form.value.alcoholError)
    }

    @Test
    fun `alcohol validation rejects non numeric input`() {
        val vm = AddEditBeerViewModel(FakeBeerRepository())

        vm.update { it.copy(alcoholPercent = "strong") }

        assertTrue(vm.form.value.alcoholError)
    }

    @Test
    fun `volume validation requires a positive integer`() {
        val vm = AddEditBeerViewModel(FakeBeerRepository())

        vm.update { it.copy(volumeMl = "0") }
        assertTrue(vm.form.value.volumeError)

        vm.update { it.copy(volumeMl = "330.5") }
        assertTrue(vm.form.value.volumeError)

        vm.update { it.copy(volumeMl = "330") }
        assertFalse(vm.form.value.volumeError)
    }

    @Test
    fun `price validation rejects negative values and accepts dot decimals`() {
        val vm = AddEditBeerViewModel(FakeBeerRepository())

        vm.update { it.copy(price = "-1") }
        assertTrue(vm.form.value.priceError)

        vm.update { it.copy(price = "29.50") }
        assertFalse(vm.form.value.priceError)
    }

    @Test
    fun `blank numeric fields have no validation errors`() {
        val vm = AddEditBeerViewModel(FakeBeerRepository())

        vm.update { it.copy(alcoholPercent = " ", volumeMl = "", price = "  ") }

        assertFalse(vm.form.value.alcoholError)
        assertFalse(vm.form.value.volumeError)
        assertFalse(vm.form.value.priceError)
    }

    @Test
    fun `invalid numeric fields prevent save`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Invalid", alcoholPercent = "101") }

        vm.save()

        assertFalse(vm.form.value.saved)
        assertTrue(repo.observeBeers().first().isEmpty())
    }

    @Test
    fun `editing form fields tracks unsaved changes`() {
        val vm = AddEditBeerViewModel(FakeBeerRepository())
        assertFalse(vm.form.value.hasUnsavedChanges)

        vm.update { it.copy(name = "Changed") }

        assertTrue(vm.form.value.hasUnsavedChanges)
    }

    @Test
    fun `loading an existing beer starts with no unsaved changes`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Falcon"))
        val vm = AddEditBeerViewModel(repo)

        vm.load("a")

        assertFalse(vm.form.value.hasUnsavedChanges)
    }

    @Test
    fun `successful save clears unsaved changes`() = runTest {
        val vm = AddEditBeerViewModel(FakeBeerRepository())
        vm.update { it.copy(name = "Falcon") }

        vm.save()

        assertFalse(vm.form.value.hasUnsavedChanges)
    }

    @Test
    fun `save exposes saving state until repository completes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = ControlledBeerRepository(saveGate = gate)
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Falcon") }

        vm.save()

        assertEquals(SaveState.Saving, vm.form.value.saveState)
        assertFalse(vm.form.value.saved)
        gate.complete(Unit)
        assertEquals(SaveState.Saved, vm.form.value.saveState)
    }

    @Test
    fun `edits made while save is pending remain unsaved after completion`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = ControlledBeerRepository(saveGate = gate)
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Falcon") }
        vm.save()

        vm.update { it.copy(name = "Falcon edited") }
        gate.complete(Unit)

        assertEquals("Falcon", repo.observeBeers().first().single().name)
        assertEquals("Falcon edited", vm.form.value.name)
        assertTrue(vm.form.value.hasUnsavedChanges)
        assertFalse(vm.form.value.saved)
    }

    @Test
    fun `save failure exposes error and retains unsaved changes`() = runTest {
        val repo = ControlledBeerRepository(saveFailure = IllegalStateException("disk full"))
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Falcon") }

        vm.save()

        val state = vm.form.value.saveState as SaveState.Error
        assertEquals("Could not save beer", state.message)
        assertFalse(vm.form.value.saved)
        assertTrue(vm.form.value.hasUnsavedChanges)
    }

    @Test
    fun `prefill from catalog fills the form and marks unsaved changes`() = runTest {
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)

        vm.prefillFromCatalog("1324515")

        val form = vm.form.value
        assertEquals("Omnipollo Prodigal Pale Ale", form.name)
        assertEquals("Omnipollo", form.brewery)
        assertEquals("Ale", form.type)
        assertEquals("5.2", form.alcoholPercent)
        assertEquals("330", form.volumeMl)
        assertEquals("25.9", form.price)
        assertEquals("1324515", form.catalogArticleNumber)
        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/50786609/50786609_400.jpg",
            form.imageUrl,
        )
        assertNull(form.grade)
        assertFalse(form.tried)
        assertTrue(form.hasUnsavedChanges)
    }

    @Test
    fun `prefill accepts the short shelf number`() = runTest {
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)

        vm.prefillFromCatalog("13245")

        assertEquals("1324515", vm.form.value.catalogArticleNumber)
    }

    @Test
    fun `prefill with an unknown number leaves the form untouched`() = runTest {
        val catalog = FakeCatalogRepository()
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)

        vm.prefillFromCatalog("999999")

        assertEquals("", vm.form.value.name)
        assertNull(vm.form.value.catalogArticleNumber)
        assertFalse(vm.form.value.hasUnsavedChanges)
    }

    @Test
    fun `saving a prefilled beer stores the catalog link and image url`() = runTest {
        val repo = FakeBeerRepository()
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(repo, catalog)

        vm.prefillFromCatalog("1324515")
        vm.setGrade(5)
        vm.save()

        val saved = repo.observeBeers().first().single()
        assertEquals("Omnipollo Prodigal Pale Ale", saved.name)
        assertEquals("1324515", saved.catalogArticleNumber)
        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/50786609/50786609_400.jpg",
            saved.imageUrl,
        )
        assertEquals(5, saved.grade)
        assertTrue(saved.tried)
    }

    @Test
    fun `prefill runs once per article number and never overwrites edits`() = runTest {
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)

        vm.prefillFromCatalog("1324515")
        vm.update { it.copy(name = "My own name") }
        vm.prefillFromCatalog("1324515")

        assertEquals("My own name", vm.form.value.name)
    }

    @Test
    fun `loading an existing beer carries its image url through an edit and save`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(
            beer(id = "a", imageUrl = "https://product-cdn.systembolaget.se/productimages/1/1_400.jpg"),
        )
        val vm = AddEditBeerViewModel(repo)

        vm.load("a")
        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/1/1_400.jpg",
            vm.form.value.imageUrl,
        )

        vm.update { it.copy(note = "still great") }
        vm.save()

        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/1/1_400.jpg",
            repo.getBeer("a")?.imageUrl,
        )
    }

    @Test
    fun `prefill from catalog is ignored while an edit session is loaded`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Loaded Beer", grade = 3))
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(repo, catalog)

        vm.load("a")
        vm.prefillFromCatalog("1324515")

        assertEquals("Loaded Beer", vm.form.value.name)
        assertEquals(3, vm.form.value.grade)
        assertTrue(vm.form.value.tried)

        vm.save()

        val saved = repo.observeBeers().first().single()
        assertEquals("a", saved.id)
        assertEquals("Loaded Beer", saved.name)
    }

    @Test
    fun `load wins when it starts right after a prefill lookup`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Loaded Beer", grade = 3))
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(repo, catalog)

        vm.prefillFromCatalog("1324515")
        vm.load("a")

        assertEquals("a", vm.form.value.id)
        assertEquals("Loaded Beer", vm.form.value.name)
    }

    @Test
    fun `typing two characters surfaces catalog suggestions in add mode`() = runTest {
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)
        backgroundScope.launch { vm.catalogSuggestions.collect { } }
        advanceUntilIdle()

        vm.update { it.copy(name = "o") }
        advanceUntilIdle()
        assertEquals(0, vm.catalogSuggestions.value.size)

        vm.update { it.copy(name = "om") }
        advanceUntilIdle()
        assertEquals(
            listOf("Omnipollo Prodigal Pale Ale"),
            vm.catalogSuggestions.value.map { it.name },
        )
    }

    @Test
    fun `suggestions are capped at eight`() = runTest {
        val catalog = FakeCatalogRepository()
        (1..10).forEach { index ->
            catalog.add(
                catalogProduct(
                    articleNumber = "100$index",
                    articleNumberShort = null,
                    name = "Lager number $index",
                ),
            )
        }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)
        backgroundScope.launch { vm.catalogSuggestions.collect { } }

        vm.update { it.copy(name = "lager") }
        advanceUntilIdle()

        assertEquals(8, vm.catalogSuggestions.value.size)
    }

    @Test
    fun `editing an existing beer never shows suggestions`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "b1", name = "Omnipollo Something"))
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(repo, catalog)
        vm.load("b1")
        backgroundScope.launch { vm.catalogSuggestions.collect { } }

        vm.update { it.copy(name = "Omnipollo") }
        advanceUntilIdle()

        assertEquals(0, vm.catalogSuggestions.value.size)
    }

    @Test
    fun `applying a product fills the form and hides suggestions until the name changes`() = runTest {
        val product = catalogProduct()
        val catalog = FakeCatalogRepository().apply { add(product) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)
        backgroundScope.launch { vm.catalogSuggestions.collect { } }

        vm.update { it.copy(name = "omni") }
        advanceUntilIdle()
        vm.applyCatalogProduct(product)
        advanceUntilIdle()

        val form = vm.form.value
        assertEquals("Omnipollo Prodigal Pale Ale", form.name)
        assertEquals("Omnipollo", form.brewery)
        assertEquals("Ale", form.type)
        assertEquals("5.2", form.alcoholPercent)
        assertEquals("330", form.volumeMl)
        assertEquals("25.9", form.price)
        assertEquals("1324515", form.catalogArticleNumber)
        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/50786609/50786609_400.jpg",
            form.imageUrl,
        )
        assertTrue(form.hasUnsavedChanges)
        assertEquals(0, vm.catalogSuggestions.value.size)

        vm.update { it.copy(name = "Omnipollo Prodigal") }
        advanceUntilIdle()
        assertEquals(1, vm.catalogSuggestions.value.size)
    }

    @Test
    fun `a scan prefill also keeps suggestions hidden for the prefilled name`() = runTest {
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)
        backgroundScope.launch { vm.catalogSuggestions.collect { } }

        vm.prefillFromCatalog("1324515")
        advanceUntilIdle()

        assertEquals("Omnipollo Prodigal Pale Ale", vm.form.value.name)
        assertEquals(0, vm.catalogSuggestions.value.size)
    }
}

private class ControlledBeerRepository(
    initialBeers: List<TriedBeer> = emptyList(),
    private val loadGate: CompletableDeferred<Unit>? = null,
    private val saveGate: CompletableDeferred<Unit>? = null,
    private val loadFailure: Throwable? = null,
    private val saveFailure: Throwable? = null,
) : BeerRepository {
    private val beers = MutableStateFlow(initialBeers.associateBy { it.id })

    var addCalls: Int = 0
        private set
    var updateCalls: Int = 0
        private set

    override fun observeBeers(): Flow<List<TriedBeer>> = beers.map { it.values.toList() }

    override suspend fun getBeer(id: String): TriedBeer? {
        loadGate?.await()
        loadFailure?.let { throw it }
        return beers.value[id]
    }

    override suspend fun addBeer(beer: TriedBeer) {
        addCalls += 1
        saveGate?.await()
        saveFailure?.let { throw it }
        beers.update { it + (beer.id to beer) }
    }

    override suspend fun updateBeer(beer: TriedBeer) {
        updateCalls += 1
        saveGate?.await()
        saveFailure?.let { throw it }
        beers.update { it + (beer.id to beer) }
    }

    override suspend fun deleteBeer(id: String) {
        beers.update { it - id }
    }
}
