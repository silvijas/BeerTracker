package com.beertracker.ui.scan

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanModeSwitchTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows both modes and reports a tap on the other one`() {
        var selected: ScanMode? = null
        composeRule.setContent {
            BeerTrackerTheme {
                ScanModeSwitch(selected = ScanMode.SHELF_LABEL, onSelect = { selected = it })
            }
        }

        composeRule.onNodeWithText("Shelf label").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText("Can").assertIsDisplayed().performClick()

        assertEquals(ScanMode.CAN, selected)
    }

    @Test
    fun `tapping the selected mode does nothing`() {
        var selected: ScanMode? = null
        composeRule.setContent {
            BeerTrackerTheme {
                ScanModeSwitch(selected = ScanMode.CAN, onSelect = { selected = it })
            }
        }

        composeRule.onNodeWithText("Can").performClick()

        assertNull(selected)
    }
}
