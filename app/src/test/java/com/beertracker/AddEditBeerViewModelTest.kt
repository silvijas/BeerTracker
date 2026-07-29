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
                alcoholPercent = "5,6", volumeMl = "330", price = "29.50",
            )
        }
        vm.setGrade(9)
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals("Punk IPA", saved.name)
        assertEquals(5.6, saved.alcoholPercent!!, 0.001)
        assertEquals(330, saved.volumeMl)
        assertEquals(29.5, saved.price!!, 0.001)
        assertEquals(9, saved.grade)
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
        vm.setGrade(8)
        assertTrue(vm.form.value.tried)
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals(8, saved.grade)
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
        vm.setGrade(9)
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
        vm.setGrade(7)
        vm.setGrade(null)
        assertNull(vm.form.value.grade)
        assertTrue(vm.form.value.tried)
    }

    @Test
    fun `an out of range grade sets gradeError and stores nothing`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Bad Grade", grade = 4, tried = true) }
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
        repo.addBeer(beer(id = "a", name = "Old Name", grade = 6, dateAdded = 111L))
        val vm = AddEditBeerViewModel(repo, clock = { 999L })
        vm.load("a")
        vm.update { it.copy(name = "New Name") }
        vm.setGrade(8)
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals("a", saved.id)
        assertEquals("New Name", saved.name)
        assertEquals(8, saved.grade)
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
}
