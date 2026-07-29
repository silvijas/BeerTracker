package com.beertracker

import com.beertracker.ui.AddEditBeerViewModel
import kotlinx.coroutines.flow.first
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
                alcoholPercent = "5,6", volumeMl = "330", price = "29.50", grade = 9,
            )
        }
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals("Punk IPA", saved.name)
        assertEquals(5.6, saved.alcoholPercent!!, 0.001)
        assertEquals(330, saved.volumeMl)
        assertEquals(29.5, saved.price!!, 0.001)
        assertEquals(9, saved.grade)
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
    fun `editing preserves id and dateAdded`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Old Name", grade = 6, dateAdded = 111L))
        val vm = AddEditBeerViewModel(repo, clock = { 999L })
        vm.load("a")
        vm.update { it.copy(name = "New Name", grade = 8) }
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals("a", saved.id)
        assertEquals("New Name", saved.name)
        assertEquals(8, saved.grade)
        assertEquals(111L, saved.dateAdded)
    }
}
