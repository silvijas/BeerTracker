package com.beertracker.ui.catalog

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import com.beertracker.FakeBeerRepository
import com.beertracker.FakeCatalogRepository
import com.beertracker.MainDispatcherRule
import com.beertracker.beer
import com.beertracker.catalogProduct
import com.beertracker.ui.theme.BeerTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatalogBrowserScreenTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Before
    fun installFakeImageLoader() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            SuccessResult(
                                drawable = ColorDrawable(Color.DKGRAY),
                                request = chain.request,
                                dataSource = DataSource.MEMORY,
                            )
                        },
                    )
                }
                .build(),
        )
    }

    @After
    fun resetImageLoader() {
        Coil.reset()
    }

    private data class Clicks(var added: String? = null, var opened: String? = null)

    private fun render(
        catalog: FakeCatalogRepository,
        beers: FakeBeerRepository = FakeBeerRepository(),
    ): Clicks {
        val clicks = Clicks()
        composeRule.setContent {
            BeerTrackerTheme {
                CatalogBrowserScreen(
                    viewModel = CatalogBrowserViewModel(catalog, beers),
                    onAddProduct = { clicks.added = it },
                    onOpenBeer = { clicks.opened = it },
                    onBack = {},
                )
            }
        }
        return clicks
    }

    private fun twoBeerCatalog() = FakeCatalogRepository().apply {
        add(catalogProduct())
        add(
            catalogProduct(
                articleNumber = "1000501",
                articleNumberShort = "10005",
                name = "Second Beer",
                brewery = "Other Brewery",
            ),
        )
    }

    @Test
    fun `rows render and searching narrows the list`() {
        render(twoBeerCatalog())

        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").assertIsDisplayed()
        composeRule.onNodeWithText("Second Beer").assertIsDisplayed()

        composeRule.onNodeWithText("Search the catalog").performTextInput("second")

        composeRule.onNodeWithText("Second Beer").assertIsDisplayed()
        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").assertDoesNotExist()
    }

    @Test
    fun `tapping an unlogged beer requests the add flow with its article number`() {
        val clicks = render(twoBeerCatalog())

        composeRule.onNodeWithText("Second Beer").performClick()

        assertEquals("1000501", clicks.added)
        assertNull(clicks.opened)
    }

    @Test
    fun `a logged beer shows its grade and opens its detail instead`() {
        val beers = FakeBeerRepository()
        runBlocking {
            beers.addBeer(beer(id = "b1", grade = 4).copy(catalogArticleNumber = "1324515"))
        }
        val clicks = render(twoBeerCatalog(), beers)

        composeRule.onNodeWithContentDescription("Grade 4 out of 5").assertIsDisplayed()
        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").performClick()

        assertEquals("b1", clicks.opened)
        assertNull(clicks.added)
    }

    @Test
    fun `an empty catalog shows the empty state`() {
        render(FakeCatalogRepository())

        composeRule.onNodeWithText("Catalog is empty").assertIsDisplayed()
    }
}
