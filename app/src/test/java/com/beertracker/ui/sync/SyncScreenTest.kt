package com.beertracker.ui.sync

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.beertracker.MainDispatcherRule
import com.beertracker.domain.SyncStatus
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class SyncScreenTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun render(
        state: SyncUiState,
        onCodeChange: (String) -> Unit = {},
        onCreate: () -> Unit = {},
        onJoin: () -> Unit = {},
        onStopSyncing: () -> Unit = {},
        onShare: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            BeerTrackerTheme {
                SyncContent(
                    state = state,
                    onCodeChange = onCodeChange,
                    onCreate = onCreate,
                    onJoin = onJoin,
                    onStopSyncing = onStopSyncing,
                    onShare = onShare,
                    onBack = {},
                )
            }
        }
    }

    private val paired = SyncStatus.Paired(
        inviteCode = "ABCDEFGH",
        memberCount = 1,
        lastSyncedUtc = null,
        hasPendingUploads = false,
    )

    @Test
    fun `unavailable build explains itself`() {
        render(SyncUiState(status = SyncStatus.Unavailable))
        composeRule.onNodeWithText("Sync is not set up in this build").assertIsDisplayed()
    }

    @Test
    fun `not paired offers create and join`() {
        var created = false
        var joined = false
        render(
            SyncUiState(status = SyncStatus.NotPaired, codeInput = "ABCD-EFGH"),
            onCreate = { created = true },
            onJoin = { joined = true },
        )

        composeRule.onNodeWithText("Create a shared cellar").performClick()
        composeRule.onNodeWithText("Join").performScrollTo().performClick()

        assertTrue(created)
        assertTrue(joined)
    }

    @Test
    fun `join is disabled until a code is typed`() {
        render(SyncUiState(status = SyncStatus.NotPaired, codeInput = ""))
        composeRule.onNodeWithText("Join").assertIsNotEnabled()
    }

    @Test
    fun `typing in the code field reports the text`() {
        var typed = ""
        render(SyncUiState(status = SyncStatus.NotPaired), onCodeChange = { typed = it })

        composeRule.onNodeWithText("Invite code").performTextInput("abcd")

        assertEquals("abcd", typed)
    }

    @Test
    fun `each error renders its text`() {
        val error = mutableStateOf<SyncError?>(null)
        composeRule.setContent {
            BeerTrackerTheme {
                SyncContent(
                    state = SyncUiState(status = SyncStatus.NotPaired, error = error.value),
                    onCodeChange = {},
                    onCreate = {},
                    onJoin = {},
                    onStopSyncing = {},
                    onShare = {},
                    onBack = {},
                )
            }
        }
        val expected = mapOf(
            SyncError.INVALID_CODE to "Codes have eight letters and digits, like ABCD-EFGH.",
            SyncError.UNKNOWN_CODE to "No cellar has that code. Check it on the other phone and try again.",
            SyncError.OFFLINE to "Could not reach the server. Check the connection and try again.",
            SyncError.UNAVAILABLE to "Sync is not set up in this build.",
            SyncError.FAILED to "Something went wrong while syncing. Try again.",
        )

        expected.forEach { (value, text) ->
            error.value = value
            composeRule.waitForIdle()
            composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `paired shows the formatted code and shares it`() {
        var shared = ""
        render(SyncUiState(status = paired), onShare = { shared = it })

        composeRule.onNodeWithText("ABCD-EFGH").assertIsDisplayed()
        composeRule.onNodeWithText("Only this phone so far.").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for the first sync.").assertIsDisplayed()
        composeRule.onNodeWithText("Share code").performClick()

        assertEquals("Join my BeerTracker cellar with the code ABCD-EFGH.", shared)
    }

    @Test
    fun `paired with two members and pending uploads says so`() {
        render(
            SyncUiState(
                status = paired.copy(memberCount = 2, lastSyncedUtc = 1_700_000_000_000L, hasPendingUploads = true),
            ),
        )

        composeRule.onNodeWithText("2 phones share this cellar.").assertIsDisplayed()
        composeRule.onNodeWithText("Changes on this phone are waiting to upload.").assertIsDisplayed()
        composeRule.onNodeWithText("Last synced", substring = true).assertIsDisplayed()
    }

    @Test
    fun `paired while connecting shows the connecting line`() {
        render(SyncUiState(status = paired.copy(memberCount = null)))
        composeRule.onNodeWithText("Connecting.").assertIsDisplayed()
    }

    @Test
    fun `stop syncing asks first and then fires`() {
        var stopped = false
        render(SyncUiState(status = paired), onStopSyncing = { stopped = true })

        composeRule.onNodeWithText("Stop syncing on this phone").performScrollTo().performClick()
        composeRule.onNodeWithText("Stop syncing?").assertIsDisplayed()
        assertFalse(stopped)
        composeRule.onNodeWithText("Stop syncing").performClick()

        assertTrue(stopped)
    }

    @Test
    fun `working disables create and join`() {
        render(SyncUiState(status = SyncStatus.NotPaired, codeInput = "ABCDEFGH", working = true))

        composeRule.onNodeWithText("Create a shared cellar").assertIsNotEnabled()
        composeRule.onNodeWithText("Join").assertIsNotEnabled()
        composeRule.onNodeWithText("Invite code").assertIsNotEnabled()
    }
}
