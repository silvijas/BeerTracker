package com.beertracker.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.beertracker.beer
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BeerThumbnailTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the placeholder without an image and without crashing`() {
        composeRule.setContent { BeerTrackerTheme { BeerThumbnail(model = null) } }
        composeRule.waitForIdle()
    }

    @Test
    fun `renders with an image and without crashing`() {
        composeRule.setContent {
            BeerTrackerTheme { BeerThumbnail(model = "https://cdn.example/beer_400.jpg") }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a list row still describes its beer with no image attached`() {
        composeRule.setContent {
            BeerTrackerTheme {
                BeerListItem(
                    beer = beer(
                        id = "a",
                        name = "Cellar Reserve",
                        brewery = "Nordic Field",
                        type = "Lager",
                        grade = 4,
                        imageUrl = null,
                    ),
                    onClick = {},
                )
            }
        }
        composeRule
            .onNodeWithContentDescription("Cellar Reserve", substring = true)
            .assertIsDisplayed()
    }
}
