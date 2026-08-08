package com.beertracker.ui.scan

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.beertracker.FakeCatalogRepository
import com.beertracker.MainDispatcherRule
import com.beertracker.catalogProduct
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanScreenTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun renderContent(
        state: ScanUiState = ScanUiState.Idle,
        permission: CameraPermission = CameraPermission.DENIED,
        onManualLookup: () -> Unit = {},
        onScanAgain: () -> Unit = {},
    ) {
        composeRule.setContent {
            BeerTrackerTheme {
                ScanContent(
                    state = state,
                    permission = permission,
                    manualInput = "13245",
                    onManualInputChange = {},
                    onManualLookup = onManualLookup,
                    onScanAgain = onScanAgain,
                    onBack = {},
                    cameraPreview = { Text("Fake camera preview") },
                )
            }
        }
    }

    @Test
    fun `denied permission shows the friendly error and keeps manual entry usable`() {
        var lookedUp = false
        renderContent(permission = CameraPermission.DENIED, onManualLookup = { lookedUp = true })

        composeRule.onNodeWithText("Camera unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Article number").assertIsDisplayed()
        composeRule.onNodeWithText("Look up").performClick()
        assertTrue(lookedUp)
    }

    @Test
    fun `granted permission composes the camera preview slot`() {
        renderContent(permission = CameraPermission.GRANTED)
        composeRule.onNodeWithText("Fake camera preview").assertIsDisplayed()
    }

    @Test
    fun `not found shows the typed number and scan again resets`() {
        var reset = false
        renderContent(state = ScanUiState.NotFound("99999"), onScanAgain = { reset = true })

        composeRule
            .onNodeWithText("Number 99999 is not in the beer catalog. Check the digits or add the beer manually.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Scan again").performScrollTo().performClick()
        assertTrue(reset)
    }

    @Test
    fun `found state names the product`() {
        renderContent(state = ScanUiState.Found(catalogProduct()))
        composeRule.onNodeWithText("Found Omnipollo Prodigal Pale Ale").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `manual lookup through the real screen navigates with the full article number`() {
        val viewModel = ScanViewModel(FakeCatalogRepository().apply { add(catalogProduct()) })
        var foundNumber: String? = null

        composeRule.setContent {
            BeerTrackerTheme {
                ScanScreen(
                    viewModel = viewModel,
                    onFound = { foundNumber = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Article number").performTextInput("13245")
        composeRule.onNodeWithText("Look up").performClick()
        composeRule.runOnIdle {
            assertEquals("1324515", foundNumber)
        }
    }
}
