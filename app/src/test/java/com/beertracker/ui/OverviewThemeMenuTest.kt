package com.beertracker.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.beertracker.FakeBeerRepository
import com.beertracker.FakeCatalogRefresher
import com.beertracker.FakeCatalogRepository
import com.beertracker.MainDispatcherRule
import com.beertracker.domain.ThemeMode
import com.beertracker.ui.theme.BeerTrackerTheme
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
class OverviewThemeMenuTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun `theme menu lists the modes and reports the chosen one`() {
        var chosen: ThemeMode? = null
        composeRule.setContent {
            BeerTrackerTheme {
                OverviewScreen(
                    viewModel = OverviewViewModel(FakeBeerRepository()),
                    catalogViewModel = CatalogRefreshViewModel(
                        FakeCatalogRepository(),
                        FakeCatalogRefresher(),
                    ),
                    onAddClick = {},
                    onBeerClick = {},
                    themeMode = ThemeMode.SYSTEM,
                    onSetThemeMode = { chosen = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Theme").performClick()
        composeRule.onNodeWithText("System default").assertIsDisplayed()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithText("Dark").performClick()

        assertEquals(ThemeMode.DARK, chosen)
    }
}
