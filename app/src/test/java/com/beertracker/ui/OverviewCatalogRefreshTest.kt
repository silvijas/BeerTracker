package com.beertracker.ui

import android.app.Application
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.beertracker.FakeBeerRepository
import com.beertracker.FakeCatalogRefresher
import com.beertracker.FakeCatalogRepository
import com.beertracker.MainDispatcherRule
import com.beertracker.domain.CatalogStatus
import com.beertracker.domain.RefreshResult
import com.beertracker.ui.theme.BeerTrackerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CompletableDeferred
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
class OverviewCatalogRefreshTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun render(
        refresher: FakeCatalogRefresher,
        catalogStatus: CatalogStatus? = CatalogStatus(beerCount = 42, lastRefreshUtc = null),
    ): FakeCatalogRefresher {
        val catalogRepository = FakeCatalogRepository().apply {
            status.value = catalogStatus
        }
        val catalogViewModel = CatalogRefreshViewModel(catalogRepository, refresher)
        composeRule.setContent {
            BeerTrackerTheme {
                OverviewScreen(
                    viewModel = OverviewViewModel(FakeBeerRepository()),
                    catalogViewModel = catalogViewModel,
                    onAddClick = {},
                    onBeerClick = {},
                )
            }
        }
        return refresher
    }

    @Test
    fun `the update dialog shows the bundled status and a success snackbar after updating`() {
        val refresher = render(
            FakeCatalogRefresher().apply {
                result = RefreshResult.Success(beerCount = 1534, refreshedUtc = 0L)
            },
        )

        composeRule.onNodeWithContentDescription("Update beer catalog").performClick()
        composeRule.onNodeWithText("Using the bundled catalog, 42 beers. Not updated yet.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Update now").performClick()

        composeRule.onNodeWithText("Catalog updated, 1534 beers").assertIsDisplayed()
        assertEquals(1, refresher.refreshCalls)
    }

    @Test
    fun `the update dialog shows the last refresh date when the catalog has already been updated`() {
        val lastRefreshUtc = 1_700_000_000_000L
        val expectedDate = Instant.ofEpochMilli(lastRefreshUtc)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

        render(
            FakeCatalogRefresher(),
            catalogStatus = CatalogStatus(beerCount = 1534, lastRefreshUtc = lastRefreshUtc),
        )

        composeRule.onNodeWithContentDescription("Update beer catalog").performClick()

        composeRule.onNodeWithText("Updated $expectedDate, 1534 beers.").assertIsDisplayed()
    }

    @Test
    fun `the update dialog shows the unknown status message when the catalog status has not loaded`() {
        render(FakeCatalogRefresher(), catalogStatus = null)

        composeRule.onNodeWithContentDescription("Update beer catalog").performClick()

        composeRule.onNodeWithText("The catalog is still being prepared.").assertIsDisplayed()
    }

    @Test
    fun `a failed update shows the calm failure message`() {
        render(
            FakeCatalogRefresher().apply {
                result = RefreshResult.Failure("Could not reach the Systembolaget catalog")
            },
        )

        composeRule.onNodeWithContentDescription("Update beer catalog").performClick()
        composeRule.onNodeWithText("Update now").performClick()

        composeRule.onNodeWithText("Could not reach the Systembolaget catalog").assertIsDisplayed()
    }

    @Test
    fun `the catalog button invokes the catalog navigation callback`() {
        var opened = false
        val catalogViewModel = CatalogRefreshViewModel(FakeCatalogRepository(), FakeCatalogRefresher())
        composeRule.setContent {
            BeerTrackerTheme {
                OverviewScreen(
                    viewModel = OverviewViewModel(FakeBeerRepository()),
                    catalogViewModel = catalogViewModel,
                    onAddClick = {},
                    onBeerClick = {},
                    onCatalogClick = { opened = true },
                )
            }
        }

        composeRule.onNodeWithText("Catalog").performClick()

        assertTrue(opened)
    }

    @Test
    fun `the top bar shows an indeterminate progress indicator while refreshing`() {
        val refresher = FakeCatalogRefresher().apply { gate = CompletableDeferred() }
        render(refresher)

        composeRule.onNodeWithContentDescription("Update beer catalog").performClick()
        composeRule.onNodeWithText("Update now").performClick()

        composeRule.onNodeWithContentDescription("Update beer catalog").assertDoesNotExist()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            ),
        ).assertIsDisplayed()

        refresher.gate?.complete(Unit)

        composeRule.onNodeWithText("Catalog updated, 1534 beers").assertIsDisplayed()
    }
}
