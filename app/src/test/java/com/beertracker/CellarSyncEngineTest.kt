package com.beertracker

import android.app.Application
import com.beertracker.data.CellarSyncEngine
import com.beertracker.domain.CellarInfo
import com.beertracker.domain.CellarMembership
import com.beertracker.domain.InviteCodes
import com.beertracker.domain.RemoteBeersUpdate
import com.beertracker.domain.RemoteChange
import com.beertracker.domain.SyncException
import com.beertracker.domain.SyncStatus
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CellarSyncEngineTest {

    /**
     * The engine's scope shares the test scheduler and runs unconfined, so
     * every emission is applied before the test's next line, and delay()
     * inside the engine moves with advanceTimeBy.
     */
    private class Harness(
        scope: TestScope,
        membership: CellarMembership? = null,
        available: Boolean = true,
        clock: () -> Long = { 1_000L },
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
            clock = clock,
            random = Random(1),
        )
    }

    private val paired = CellarMembership("cellar-1", "ABCDEFGH")

    private fun update(
        vararg changes: RemoteChange,
        fromServer: Boolean = true,
        pending: Boolean = false,
    ) = RemoteBeersUpdate(changes.toList(), fromServer = fromServer, hasPendingWrites = pending)

    @Test
    fun `status is unavailable when the remote is`() = runTest {
        val h = Harness(this, membership = paired, available = false)
        h.engine.start()
        assertEquals(SyncStatus.Unavailable, h.engine.status.value)
    }

    @Test
    fun `status is not paired without a membership`() = runTest {
        val h = Harness(this)
        h.engine.start()
        assertEquals(SyncStatus.NotPaired, h.engine.status.value)
    }

    @Test
    fun `createCellar signs in, creates, saves and uploads every local beer`() = runTest {
        val h = Harness(this)
        h.local.addBeer(beer(id = "a"))
        h.local.addBeer(beer(id = "b"))
        h.engine.start()

        h.engine.createCellar()

        assertEquals(1, h.remote.signIns)
        val membership = h.store.membership.value!!
        assertEquals("cellar-1", membership.cellarId)
        assertEquals(InviteCodes.generate(Random(1)), membership.inviteCode)
        assertEquals(listOf("user-1"), h.remote.cellars["cellar-1"])
        assertEquals("cellar-1", h.remote.invites[membership.inviteCode])
        assertEquals(setOf("a", "b"), h.remote.puts.map { it.second.id }.toSet())
        assertTrue(h.remote.puts.all { it.first == "cellar-1" })
        val status = h.engine.status.value as SyncStatus.Paired
        assertEquals(membership.inviteCode, status.inviteCode)
        assertNull(status.memberCount)
        assertNull(status.lastSyncedUtc)
        assertFalse(status.hasPendingUploads)
    }

    @Test
    fun `createCellar refuses when the remote is unavailable`() = runTest {
        val h = Harness(this, available = false)
        h.engine.start()

        try {
            h.engine.createCellar()
            fail("expected Unavailable")
        } catch (expected: SyncException.Unavailable) {
        }
        assertEquals(0, h.remote.signIns)
        assertNull(h.store.membership.value)
    }

    @Test
    fun `joinCellar with a malformed code fails before touching the remote`() = runTest {
        val h = Harness(this)
        h.engine.start()

        try {
            h.engine.joinCellar("ABC")
            fail("expected InvalidCode")
        } catch (expected: SyncException.InvalidCode) {
        }
        assertEquals(0, h.remote.signIns)
        assertEquals(SyncStatus.NotPaired, h.engine.status.value)
    }

    @Test
    fun `joinCellar with an unknown code leaves the phone not paired`() = runTest {
        val h = Harness(this)
        h.engine.start()

        try {
            h.engine.joinCellar("ABCD-EFGH")
            fail("expected UnknownCode")
        } catch (expected: SyncException.UnknownCode) {
        }
        assertNull(h.store.membership.value)
        assertEquals(SyncStatus.NotPaired, h.engine.status.value)
    }

    @Test
    fun `joinCellar with a good code joins, saves and uploads`() = runTest {
        val h = Harness(this)
        h.remote.cellars["cellar-9"] = mutableListOf("user-other")
        h.remote.invites["ABCDEFGH"] = "cellar-9"
        h.local.addBeer(beer(id = "a"))
        h.engine.start()

        h.engine.joinCellar(" abcd-efgh ")

        assertEquals(CellarMembership("cellar-9", "ABCDEFGH"), h.store.membership.value)
        assertEquals(listOf("user-other", "user-1"), h.remote.cellars["cellar-9"])
        assertEquals(listOf("cellar-9" to "a"), h.remote.puts.map { it.first to it.second.id })
        assertEquals("ABCDEFGH", (h.engine.status.value as SyncStatus.Paired).inviteCode)
    }

    @Test
    fun `a remote upsert of a new id adds the beer locally`() = runTest {
        val h = Harness(this, membership = paired)
        h.engine.start()

        h.remote.emitBeers("cellar-1", update(RemoteChange.Upsert(beer(id = "r1", name = "Remote"))))

        assertEquals("Remote", h.local.getBeer("r1")?.name)
    }

    @Test
    fun `a remote upsert of a known id updates it and keeps the local photo`() = runTest {
        val h = Harness(this, membership = paired)
        h.local.addBeer(beer(id = "a", grade = 3).copy(photoUri = "file:///p.jpg"))
        h.engine.start()

        h.remote.emitBeers("cellar-1", update(RemoteChange.Upsert(beer(id = "a", grade = 5))))

        val stored = h.local.getBeer("a")!!
        assertEquals(5, stored.grade)
        assertEquals("file:///p.jpg", stored.photoUri)
    }

    @Test
    fun `a remote remove deletes the beer locally`() = runTest {
        val h = Harness(this, membership = paired)
        h.local.addBeer(beer(id = "a"))
        h.engine.start()

        h.remote.emitBeers("cellar-1", update(RemoteChange.Remove("a")))

        assertNull(h.local.getBeer("a"))
    }

    @Test
    fun `remote changes are not pushed back`() = runTest {
        val h = Harness(this, membership = paired)
        h.engine.start()

        h.remote.emitBeers(
            "cellar-1",
            update(RemoteChange.Upsert(beer(id = "r1")), RemoteChange.Remove("x")),
        )

        assertTrue(h.remote.puts.isEmpty())
        assertTrue(h.remote.deletes.isEmpty())
    }

    @Test
    fun `pushBeer and pushDelete reach the remote only while paired`() = runTest {
        val h = Harness(this)
        h.engine.start()

        h.engine.pushBeer(beer(id = "a"))
        h.engine.pushDelete("a")
        assertTrue(h.remote.puts.isEmpty())
        assertTrue(h.remote.deletes.isEmpty())

        h.store.save(paired)
        h.engine.pushBeer(beer(id = "a"))
        h.engine.pushDelete("b")

        assertEquals(listOf("cellar-1" to "a"), h.remote.puts.map { it.first to it.second.id })
        assertEquals(listOf("cellar-1" to "b"), h.remote.deletes)
    }

    @Test
    fun `a server update stamps the sync time and mirrors pending writes`() = runTest {
        val h = Harness(this, membership = paired, clock = { 4_242L })
        h.engine.start()

        h.remote.emitBeers("cellar-1", update(fromServer = false, pending = true))
        var status = h.engine.status.value as SyncStatus.Paired
        assertNull(status.lastSyncedUtc)
        assertTrue(status.hasPendingUploads)

        h.remote.emitBeers("cellar-1", update(fromServer = true, pending = false))
        status = h.engine.status.value as SyncStatus.Paired
        assertEquals(4_242L, status.lastSyncedUtc)
        assertFalse(status.hasPendingUploads)
    }

    @Test
    fun `the cellar record's member count shows in the status`() = runTest {
        val h = Harness(this, membership = paired)
        h.engine.start()

        h.remote.cellarInfo.value = CellarInfo("ABCDEFGH", memberCount = 2)

        assertEquals(2, (h.engine.status.value as SyncStatus.Paired).memberCount)
    }

    @Test
    fun `pairedUserId is the remote user only while paired`() = runTest {
        val h = Harness(this)
        h.remote.userId = "user-1"
        h.engine.start()

        assertNull(h.engine.pairedUserId)
        h.store.save(paired)
        assertEquals("user-1", h.engine.pairedUserId)
    }

    @Test
    fun `stopSyncing forgets the membership and stops applying remote changes`() = runTest {
        val h = Harness(this, membership = paired)
        h.engine.start()

        h.engine.stopSyncing()
        h.remote.emitBeers("cellar-1", update(RemoteChange.Upsert(beer(id = "late"))))

        assertNull(h.store.membership.value)
        assertEquals(SyncStatus.NotPaired, h.engine.status.value)
        assertNull(h.local.getBeer("late"))
    }

    @Test
    fun `an engine started with a stored membership listens without user action`() = runTest {
        val h = Harness(this, membership = paired)

        h.engine.start()

        assertEquals(1, h.remote.beersSubscriptions)
        h.remote.emitBeers("cellar-1", update(RemoteChange.Upsert(beer(id = "r1"))))
        assertEquals(listOf("r1"), h.local.observeBeers().first().map { it.id })
    }

    @Test
    fun `start is idempotent`() = runTest {
        val h = Harness(this, membership = paired)

        h.engine.start()
        h.engine.start()

        assertEquals(1, h.remote.beersSubscriptions)
    }

    @Test
    fun `a failing listener restarts after thirty seconds`() = runTest {
        val h = Harness(this, membership = paired)
        h.remote.beersFailuresLeft = 1

        h.engine.start()
        assertEquals(1, h.remote.beersSubscriptions)
        assertTrue(h.engine.status.value is SyncStatus.Paired)

        advanceTimeBy(30_001)

        assertEquals(2, h.remote.beersSubscriptions)
        h.remote.emitBeers("cellar-1", update(RemoteChange.Upsert(beer(id = "r1"))))
        assertEquals("Beer r1", h.local.getBeer("r1")?.name)
    }
}
