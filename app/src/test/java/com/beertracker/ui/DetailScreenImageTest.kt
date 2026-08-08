package com.beertracker.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.beertracker.FakeBeerRepository
import com.beertracker.MainDispatcherRule
import com.beertracker.beer
import com.beertracker.ui.theme.BeerTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailScreenImageTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun `detail shows the product image when the beer has an image url`() {
        val repo = FakeBeerRepository()
        runBlocking {
            repo.addBeer(
                beer(
                    id = "a",
                    name = "Punk IPA",
                    imageUrl = "https://cdn.example.invalid/productimages/1/1_400.jpg",
                ),
            )
        }

        composeRule.setContent {
            BeerTrackerTheme {
                DetailScreen(
                    viewModel = DetailViewModel(repo, "a"),
                    onEdit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Product image for Punk IPA").assertIsDisplayed()
    }

    @Test
    fun `detail shows no image block when the beer has none`() {
        val repo = FakeBeerRepository()
        runBlocking { repo.addBeer(beer(id = "a", name = "Punk IPA", imageUrl = null)) }

        composeRule.setContent {
            BeerTrackerTheme {
                DetailScreen(
                    viewModel = DetailViewModel(repo, "a"),
                    onEdit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Product image for Punk IPA").assertDoesNotExist()
    }
}
