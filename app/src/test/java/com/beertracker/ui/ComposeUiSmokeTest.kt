package com.beertracker.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.beertracker.FakeBeerRepository
import com.beertracker.MainDispatcherRule
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import com.beertracker.ui.components.BeerListItem
import com.beertracker.ui.components.EmptyState
import com.beertracker.ui.components.FlagToggleRow
import com.beertracker.ui.components.GradeMark
import com.beertracker.ui.theme.BeerTrackerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeUiSmokeTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun `grade mark exposes graded and not tried states`() {
        composeRule.setContent {
            BeerTrackerTheme {
                Column {
                    GradeMark(grade = 8, tried = true)
                    GradeMark(grade = null, tried = false)
                }
            }
        }

        composeRule.onNodeWithContentDescription("Grade 8 out of 10")
            .assertContentDescriptionEquals("Grade 8 out of 10")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Not tried").assertIsDisplayed()
    }

    @Test
    fun `flag toggle row toggles from its label`() {
        composeRule.setContent {
            BeerTrackerTheme {
                var checked by remember { mutableStateOf(false) }
                FlagToggleRow(
                    label = "Favourite",
                    checked = checked,
                    onCheckedChange = { checked = it },
                )
            }
        }

        composeRule.onNodeWithText("Favourite")
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    @Test
    fun `empty state invokes its visible action`() {
        composeRule.setContent {
            BeerTrackerTheme {
                var status by remember { mutableStateOf("Waiting") }
                Column {
                    EmptyState(
                        title = "Nothing here",
                        message = "Add your first beer.",
                        actionLabel = "Add beer",
                        onAction = { status = "Action invoked" },
                    )
                    Text(status)
                }
            }
        }

        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
        composeRule.onNodeWithText("Add beer").performClick()
        composeRule.onNodeWithText("Action invoked").assertIsDisplayed()
    }

    @Test
    fun `beer list item is clickable with a clean description without subtitle`() {
        composeRule.setContent {
            BeerTrackerTheme {
                var opened by remember { mutableStateOf(false) }
                Column {
                    BeerListItem(
                        beer = beerWithoutSubtitle(),
                        onClick = { opened = true },
                    )
                    if (opened) Text("Opened")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Cellar lager. Not tried")
            .assertContentDescriptionEquals("Cellar lager. Not tried")
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("Opened").assertIsDisplayed()
    }

    @Test
    fun `overview empty cellar exposes and invokes add action`() {
        val viewModel = OverviewViewModel(FakeBeerRepository())

        composeRule.setContent {
            BeerTrackerTheme {
                var addInvoked by remember { mutableStateOf(false) }
                if (addInvoked) {
                    Text("Add action invoked")
                } else {
                    OverviewScreen(
                        viewModel = viewModel,
                        onAddClick = { addInvoked = true },
                        onBeerClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Your cellar is ready").assertIsDisplayed()
        composeRule.onNodeWithText("Add beer").performClick()
        composeRule.onNodeWithText("Add action invoked").assertIsDisplayed()
    }

    @Test
    fun `overview error offers retry and recovers`() {
        var subscriptions = 0
        val repository = object : BeerRepository {
            override fun observeBeers(): Flow<List<TriedBeer>> {
                subscriptions += 1
                return if (subscriptions == 1) {
                    flow { throw IllegalStateException("Observation failed") }
                } else {
                    flowOf(emptyList())
                }
            }

            override suspend fun getBeer(id: String): TriedBeer? = null

            override suspend fun addBeer(beer: TriedBeer) = Unit

            override suspend fun updateBeer(beer: TriedBeer) = Unit

            override suspend fun deleteBeer(id: String) = Unit
        }
        val viewModel = OverviewViewModel(repository)

        composeRule.setContent {
            BeerTrackerTheme {
                OverviewScreen(
                    viewModel = viewModel,
                    onAddClick = {},
                    onBeerClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Could not load your cellar").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.onNodeWithText("Your cellar is ready").assertIsDisplayed()
    }

    @Test
    fun `overview scan action invokes the scan callback`() {
        val viewModel = OverviewViewModel(FakeBeerRepository())

        composeRule.setContent {
            BeerTrackerTheme {
                var scanInvoked by remember { mutableStateOf(false) }
                if (scanInvoked) {
                    Text("Scan action invoked")
                } else {
                    OverviewScreen(
                        viewModel = viewModel,
                        onAddClick = {},
                        onBeerClick = {},
                        onScanClick = { scanInvoked = true },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Scan").performClick()
        composeRule.onNodeWithText("Scan action invoked").assertIsDisplayed()
    }

    private fun beerWithoutSubtitle() = TriedBeer(
        id = "beer",
        name = "Cellar lager",
        brewery = "",
        type = " ",
        alcoholPercent = null,
        volumeMl = null,
        price = null,
        grade = null,
        tried = false,
        note = "",
        aftertaste = "",
        goesWellWith = emptyList(),
        buyAgain = false,
        favourite = false,
        dateAdded = 0L,
        catalogArticleNumber = null,
        addedBy = null,
        imageUrl = null,
    )
}
