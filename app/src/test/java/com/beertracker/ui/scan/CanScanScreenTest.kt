package com.beertracker.ui.scan

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import com.beertracker.MainDispatcherRule
import com.beertracker.catalogProduct
import com.beertracker.ui.components.CatalogRow
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class CanScanScreenTest {

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

    private class Actions {
        var pickedRow: CatalogRow? = null
        var startedOver = false
        var addedManually = false
        var switchedToShelfLabel = false
    }

    private fun render(
        state: CanScanUiState = CanScanUiState(),
        permission: CameraPermission = CameraPermission.GRANTED,
    ): Actions {
        val actions = Actions()
        composeRule.setContent {
            BeerTrackerTheme {
                CanScanContent(
                    state = state,
                    permission = permission,
                    onPickRow = { actions.pickedRow = it },
                    onStartOver = { actions.startedOver = true },
                    onAddManually = { actions.addedManually = true },
                    onSwitchToShelfLabel = { actions.switchedToShelfLabel = true },
                    onBack = {},
                    cameraPreview = { Text("Fake camera preview") },
                )
            }
        }
        return actions
    }

    @Test
    fun `denied permission shows the error and keeps add manually usable`() {
        val actions = render(permission = CameraPermission.DENIED)

        composeRule.onNodeWithText("Camera unavailable").assertIsDisplayed()
        composeRule
            .onNodeWithText("Reading a can needs the camera. Allow camera access in system settings, or add the beer manually.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Start over").assertDoesNotExist()
        composeRule.onNodeWithText("Add manually").performScrollTo().performClick()

        assertTrue(actions.addedManually)
    }

    @Test
    fun `granted permission composes the camera preview and the nothing read hint`() {
        render()

        composeRule.onNodeWithText("Fake camera preview").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing read yet.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Start over").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `text without a match shows the no match message and enables start over`() {
        val actions = render(CanScanUiState(hasText = true, guessedName = "SOMETHING"))

        composeRule
            .onNodeWithText("No catalog beer matches the text read so far. Turn the can, or add the beer manually.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Start over").performScrollTo().assertIsEnabled().performClick()

        assertTrue(actions.startedOver)
    }

    @Test
    fun `a candidate row shows the beer and tapping it passes the row`() {
        val row = CatalogRow(product = catalogProduct(), triedBeerId = null, grade = null, tried = false)
        val actions = render(CanScanUiState(hasText = true, guessedName = "OMNIPOLLO", matches = listOf(row)))

        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").performScrollTo().performClick()

        assertEquals(row, actions.pickedRow)
        composeRule.onNodeWithText("Nothing read yet.").assertDoesNotExist()
    }

    @Test
    fun `the mode switch reports shelf label`() {
        val actions = render()

        composeRule.onNodeWithText("Shelf label").performClick()

        assertTrue(actions.switchedToShelfLabel)
    }
}
