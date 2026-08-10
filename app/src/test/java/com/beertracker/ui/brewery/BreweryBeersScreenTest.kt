package com.beertracker.ui.brewery

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class BreweryBeersScreenTest {

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
        breweryName: String = "Omnipollo",
    ): Clicks {
        val clicks = Clicks()
        composeRule.setContent {
            BeerTrackerTheme {
                BreweryBeersScreen(
                    viewModel = BreweryBeersViewModel(catalog, beers, breweryName),
                    breweryName = breweryName,
                    onAddProduct = { clicks.added = it },
                    onOpenBeer = { clicks.opened = it },
                    onBack = {},
                )
            }
        }
        return clicks
    }

    private fun omnipolloCatalog() = FakeCatalogRepository().apply {
        add(catalogProduct(brewery = "Omnipollo"))
        add(
            catalogProduct(
                articleNumber = "2000501", articleNumberShort = "20005",
                name = "Omnipollo Bianca", brewery = "Omnipollo", type = "Sour",
            ),
        )
    }

    @Test
    fun `title is the brewery name and only its beers are listed`() {
        val catalog = omnipolloCatalog().apply {
            add(
                catalogProduct(
                    articleNumber = "3000501", articleNumberShort = "30005",
                    name = "Other Brewery Lager", brewery = "Other Brewery",
                ),
            )
        }
        render(catalog)

        composeRule.onNodeWithText("Omnipollo").assertIsDisplayed()
        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").assertIsDisplayed()
        composeRule.onNodeWithText("Omnipollo Bianca").assertIsDisplayed()
        composeRule.onNodeWithText("Other Brewery Lager").assertDoesNotExist()
    }

    @Test
    fun `tapping an untried beer requests the add flow with its article number`() {
        val clicks = render(omnipolloCatalog())

        composeRule.onNodeWithText("Omnipollo Bianca").performClick()

        assertEquals("2000501", clicks.added)
        assertNull(clicks.opened)
    }

    @Test
    fun `tapping a tried beer opens its detail instead`() {
        val beers = FakeBeerRepository()
        runBlocking {
            beers.addBeer(beer(id = "b1").copy(catalogArticleNumber = "1324515"))
        }
        val clicks = render(omnipolloCatalog(), beers)

        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").performClick()

        assertEquals("b1", clicks.opened)
        assertNull(clicks.added)
    }

    @Test
    fun `the show filter hides untried beers`() {
        val beers = FakeBeerRepository()
        runBlocking {
            beers.addBeer(beer(id = "b1").copy(catalogArticleNumber = "1324515"))
        }
        render(omnipolloCatalog(), beers)

        composeRule.onNodeWithText("Show All").performClick()
        composeRule.onNodeWithText("Tried").performClick()

        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").assertIsDisplayed()
        composeRule.onNodeWithText("Omnipollo Bianca").assertDoesNotExist()
    }

    @Test
    fun `the sort menu reorders by type`() {
        val catalog = FakeCatalogRepository().apply {
            add(
                catalogProduct(
                    articleNumber = "1", articleNumberShort = "1",
                    name = "Zeta Wheat", brewery = "Zeta Bryggeri", type = "Wheat beer",
                ),
            )
            add(
                catalogProduct(
                    articleNumber = "2", articleNumberShort = "2",
                    name = "Zeta Ale", brewery = "Zeta Bryggeri", type = "Ale",
                ),
            )
        }
        render(catalog, breweryName = "Zeta Bryggeri")

        composeRule.onNodeWithText("Sort by Name").performClick()
        composeRule.onNodeWithText("Type").performClick()

        composeRule.onNodeWithText("Sort by Type").assertIsDisplayed()
    }

    @Test
    fun `no catalog beers for the brewery shows the empty state`() {
        render(omnipolloCatalog(), breweryName = "Nobody Brews This")

        composeRule.onNodeWithText("No beers found").assertIsDisplayed()
    }

    @Test
    fun `tapping show all recovers from the filtered empty state`() {
        render(omnipolloCatalog())

        composeRule.onNodeWithText("Show All").performClick()
        composeRule.onNodeWithText("Tried").performClick()

        composeRule.onNodeWithText("You have not tried any beers from Omnipollo yet.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("Show all").performClick()

        composeRule.onNodeWithText("Omnipollo Bianca").assertIsDisplayed()
    }
}
