package com.beertracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.PrefsSyncMembershipStore
import com.beertracker.domain.CellarMembership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsSyncMembershipStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `starts with no membership`() {
        assertNull(PrefsSyncMembershipStore(context).membership.value)
    }

    @Test
    fun `keeps a saved membership across instances`() {
        PrefsSyncMembershipStore(context).save(CellarMembership("cellar-1", "ABCDEFGH"))

        assertEquals(
            CellarMembership("cellar-1", "ABCDEFGH"),
            PrefsSyncMembershipStore(context).membership.value,
        )
    }

    @Test
    fun `clear forgets the membership for this and later instances`() {
        val store = PrefsSyncMembershipStore(context)
        store.save(CellarMembership("cellar-1", "ABCDEFGH"))

        store.clear()

        assertNull(store.membership.value)
        assertNull(PrefsSyncMembershipStore(context).membership.value)
    }

    @Test
    fun `a half-written pair reads as not paired`() {
        context.getSharedPreferences("sync", Context.MODE_PRIVATE)
            .edit()
            .putString("cellar_id", "cellar-1")
            .commit()

        assertNull(PrefsSyncMembershipStore(context).membership.value)
    }
}
