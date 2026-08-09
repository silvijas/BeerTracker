package com.beertracker.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.beertracker.FakeBeerRepository
import com.beertracker.FakeCatalogRepository
import com.beertracker.MainDispatcherRule
import com.beertracker.beer
import com.beertracker.catalogProduct
import com.beertracker.ui.theme.BeerTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddEditCatalogSearchTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun `typing a name shows suggestions and picking one fills the form`() {
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)
        composeRule.setContent {
            BeerTrackerTheme {
                AddEditScreen(viewModel = vm, beerId = null, onDone = {})
            }
        }

        composeRule.onNodeWithText("Name *").performTextInput("Omni")

        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").assertIsDisplayed()
        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").performClick()

        assertEquals("1324515", vm.form.value.catalogArticleNumber)
        assertEquals("Omnipollo", vm.form.value.brewery)
        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").assertIsDisplayed()
        assertEquals(0, vm.catalogSuggestions.value.size)
    }

    @Test
    fun `editing an existing beer shows no suggestions while typing`() {
        val repo = FakeBeerRepository()
        runBlocking { repo.addBeer(beer(id = "b1", name = "My Beer")) }
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(repo, catalog)
        composeRule.setContent {
            BeerTrackerTheme {
                AddEditScreen(viewModel = vm, beerId = "b1", onDone = {})
            }
        }

        composeRule.onNodeWithText("Name *").performTextInput("Omni")

        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").assertDoesNotExist()
    }
}
