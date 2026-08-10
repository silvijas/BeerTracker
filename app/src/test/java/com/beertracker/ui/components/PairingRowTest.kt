package com.beertracker.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class PairingRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows a label for a known pairing`() {
        composeRule.setContent {
            BeerTrackerTheme { PairingRow(listOf("Pork", "Social drink"), emptyText = "none") }
        }
        composeRule.onNodeWithText("Pork").assertIsDisplayed()
        composeRule.onNodeWithText("Social drink").assertIsDisplayed()
    }

    @Test
    fun `shows a custom pairing verbatim`() {
        composeRule.setContent {
            BeerTrackerTheme { PairingRow(listOf("Tacos"), emptyText = "none") }
        }
        composeRule.onNodeWithText("Tacos").assertIsDisplayed()
    }

    @Test
    fun `shows the empty text when there are no pairings`() {
        composeRule.setContent {
            BeerTrackerTheme { PairingRow(emptyList(), emptyText = "No pairings recorded.") }
        }
        composeRule.onNodeWithText("No pairings recorded.").assertIsDisplayed()
    }

    @Test
    fun `shows both known and custom pairings together`() {
        composeRule.setContent {
            BeerTrackerTheme {
                PairingRow(listOf("Tacos", "Social drink", "Pork"), emptyText = "none")
            }
        }
        composeRule.onNodeWithText("Pork").assertIsDisplayed()
        composeRule.onNodeWithText("Social drink").assertIsDisplayed()
        composeRule.onNodeWithText("Tacos").assertIsDisplayed()
    }
}
