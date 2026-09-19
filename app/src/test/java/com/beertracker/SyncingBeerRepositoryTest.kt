package com.beertracker

import android.app.Application
import com.beertracker.data.CellarSyncEngine
import com.beertracker.data.SyncingBeerRepository
import com.beertracker.domain.CellarMembership
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SyncingBeerRepositoryTest {

    private class Harness(scope: TestScope, membership: CellarMembership?) {
        val local = FakeBeerRepository()
        val remote = FakeCellarRemote().apply { userId = "user-1" }
        val engine = CellarSyncEngine(
            local = local,
            remote = remote,
            membershipStore = FakeSyncMembershipStore(membership),
            scope = CoroutineScope(
                scope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(scope.testScheduler),
            ),
            random = Random(1),
        )
        val repository = SyncingBeerRepository(local, engine)
    }

    private val paired = CellarMembership("cellar-1", "ABCDEFGH")

    @Test
    fun `add stamps a missing addedBy with the paired user and pushes`() = runTest {
        val h = Harness(this, paired)

        h.repository.addBeer(beer(id = "a"))

        assertEquals("user-1", h.local.getBeer("a")?.addedBy)
        assertEquals("user-1", h.remote.puts.single().second.addedBy)
        assertEquals("cellar-1", h.remote.puts.single().first)
    }

    @Test
    fun `add keeps an existing addedBy`() = runTest {
        val h = Harness(this, paired)

        h.repository.addBeer(beer(id = "a").copy(addedBy = "someone"))

        assertEquals("someone", h.local.getBeer("a")?.addedBy)
        assertEquals("someone", h.remote.puts.single().second.addedBy)
    }

    @Test
    fun `add while not paired writes locally with a null addedBy and pushes nothing`() = runTest {
        val h = Harness(this, membership = null)

        h.repository.addBeer(beer(id = "a"))

        assertNull(h.local.getBeer("a")?.addedBy)
        assertTrue(h.remote.puts.isEmpty())
    }

    @Test
    fun `update writes locally and pushes the same beer`() = runTest {
        val h = Harness(this, paired)
        h.local.addBeer(beer(id = "a", grade = 3))

        h.repository.updateBeer(beer(id = "a", grade = 5))

        assertEquals(5, h.local.getBeer("a")?.grade)
        assertEquals(5, h.remote.puts.single().second.grade)
    }

    @Test
    fun `delete removes locally and pushes the delete`() = runTest {
        val h = Harness(this, paired)
        h.local.addBeer(beer(id = "a"))

        h.repository.deleteBeer("a")

        assertNull(h.local.getBeer("a"))
        assertEquals(listOf("cellar-1" to "a"), h.remote.deletes)
    }

    @Test
    fun `observe and get read straight from the local store`() = runTest {
        val h = Harness(this, paired)
        h.local.addBeer(beer(id = "a"))

        assertEquals(listOf("a"), h.repository.observeBeers().first().map { it.id })
        assertEquals("a", h.repository.getBeer("a")?.id)
    }
}
