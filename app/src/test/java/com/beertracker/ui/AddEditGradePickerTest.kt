package com.beertracker.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.beertracker.FakeBeerRepository
import com.beertracker.MainDispatcherRule
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
class AddEditGradePickerTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun render(vm: AddEditBeerViewModel) {
        composeRule.setContent {
            BeerTrackerTheme {
                AddEditScreen(viewModel = vm, beerId = null, prefillArticle = null, onDone = {})
            }
        }
    }

    @Test
    fun `tapping a can sets that grade and tapping it again clears it`() {
        val vm = AddEditBeerViewModel(FakeBeerRepository())
        render(vm)

        composeRule.onNodeWithContentDescription("Grade 3 out of 5").performScrollTo().performClick()
        assertEquals(3, vm.form.value.grade)

        composeRule.onNodeWithContentDescription("Grade 3 out of 5").performScrollTo().performClick()
        assertNull(vm.form.value.grade)
    }

    @Test
    fun `only five cans are shown`() {
        val vm = AddEditBeerViewModel(FakeBeerRepository())
        render(vm)

        composeRule.onNodeWithContentDescription("Grade 5 out of 5").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Grade 6 out of 5").assertDoesNotExist()
    }
}
