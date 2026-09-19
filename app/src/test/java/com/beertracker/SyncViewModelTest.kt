package com.beertracker

import android.app.Application
import com.beertracker.data.CellarSyncEngine
import com.beertracker.domain.CellarMembership
import com.beertracker.domain.SyncException
import com.beertracker.domain.SyncStatus
import com.beertracker.ui.sync.SyncError
import com.beertracker.ui.sync.SyncViewModel
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SyncViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        scope: TestScope,
        membership: CellarMembership? = null,
        available: Boolean = true,
    ) {
        val local = FakeBeerRepository()
        val remote = FakeCellarRemote(isAvailable = available)
        val store = FakeSyncMembershipStore(membership)
        val engine = CellarSyncEngine(
            local = local,
            remote = remote,
            membershipStore = store,
            scope = CoroutineScope(
                scope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(scope.testScheduler),
            ),
            random = Random(1),
        ).also { it.start() }
        val vm = SyncViewModel(engine)

        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.uiState.collect() }
        }
    }

    private val paired = CellarMembership("cellar-1", "ABCDEFGH")

    @Test
    fun `mirrors the engine status`() = runTest {
        val h = Harness(this, membership = paired)
        assertEquals("ABCDEFGH", (h.vm.uiState.value.status as SyncStatus.Paired).inviteCode)

        h.engine.stopSyncing()

        assertEquals(SyncStatus.NotPaired, h.vm.uiState.value.status)
    }

    @Test
    fun `createCellar lands on paired with no error`() = runTest {
        val h = Harness(this)

        h.vm.createCellar()

        val state = h.vm.uiState.value
        assertTrue(state.status is SyncStatus.Paired)
        assertFalse(state.working)
        assertNull(state.error)
    }

    @Test
    fun `a malformed code shows the invalid code error`() = runTest {
        val h = Harness(this)
        h.vm.setCodeInput("abc")

        h.vm.join()

        assertEquals(SyncError.INVALID_CODE, h.vm.uiState.value.error)
        assertEquals(SyncStatus.NotPaired, h.vm.uiState.value.status)
    }

    @Test
    fun `an unknown code shows the unknown code error`() = runTest {
        val h = Harness(this)
        h.vm.setCodeInput("ABCD-EFGH")

        h.vm.join()

        assertEquals(SyncError.UNKNOWN_CODE, h.vm.uiState.value.error)
    }

    @Test
    fun `a good code joins and clears the field`() = runTest {
        val h = Harness(this)
        h.remote.cellars["cellar-9"] = mutableListOf("user-other")
        h.remote.invites["ABCDEFGH"] = "cellar-9"
        h.vm.setCodeInput("abcd-efgh")

        h.vm.join()

        assertEquals("", h.vm.uiState.value.codeInput)
        assertEquals("ABCDEFGH", (h.vm.uiState.value.status as SyncStatus.Paired).inviteCode)
    }

    @Test
    fun `an offline failure shows the offline error`() = runTest {
        val h = Harness(this)
        h.remote.signInError = SyncException.Offline()

        h.vm.createCellar()

        assertEquals(SyncError.OFFLINE, h.vm.uiState.value.error)
        assertFalse(h.vm.uiState.value.working)
    }

    @Test
    fun `an unavailable build shows the unavailable error`() = runTest {
        val h = Harness(this, available = false)

        h.vm.createCellar()

        assertEquals(SyncError.UNAVAILABLE, h.vm.uiState.value.error)
        assertEquals(SyncStatus.Unavailable, h.vm.uiState.value.status)
    }

    @Test
    fun `an unexpected exception shows the generic error`() = runTest {
        val h = Harness(this)
        h.remote.signInError = IllegalStateException("boom")

        h.vm.createCellar()

        assertEquals(SyncError.FAILED, h.vm.uiState.value.error)
        assertFalse(h.vm.uiState.value.working)
    }

    @Test
    fun `typing upper-cases the code and clears the error`() = runTest {
        val h = Harness(this)
        h.vm.setCodeInput("abc")
        h.vm.join()
        assertEquals(SyncError.INVALID_CODE, h.vm.uiState.value.error)

        h.vm.setCodeInput("abcd")

        assertEquals("ABCD", h.vm.uiState.value.codeInput)
        assertNull(h.vm.uiState.value.error)
    }

    @Test
    fun `dismissError clears the error`() = runTest {
        val h = Harness(this)
        h.vm.setCodeInput("abc")
        h.vm.join()

        h.vm.dismissError()

        assertNull(h.vm.uiState.value.error)
    }

    @Test
    fun `taps while working are ignored`() = runTest {
        val h = Harness(this)
        h.remote.signInGate = CompletableDeferred()

        h.vm.createCellar()
        assertTrue(h.vm.uiState.value.working)
        h.vm.createCellar()
        h.remote.signInGate?.complete(Unit)

        assertEquals(1, h.remote.signIns)
        assertFalse(h.vm.uiState.value.working)
    }

    @Test
    fun `stopSyncing lands on not paired`() = runTest {
        val h = Harness(this, membership = paired)

        h.vm.stopSyncing()

        assertEquals(SyncStatus.NotPaired, h.vm.uiState.value.status)
    }
}
