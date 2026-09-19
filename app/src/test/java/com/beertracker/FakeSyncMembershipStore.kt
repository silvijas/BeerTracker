package com.beertracker

import com.beertracker.domain.CellarMembership
import com.beertracker.domain.SyncMembershipStore
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSyncMembershipStore(initial: CellarMembership? = null) : SyncMembershipStore {
    override val membership = MutableStateFlow(initial)

    override fun save(membership: CellarMembership) {
        this.membership.value = membership
    }

    override fun clear() {
        membership.value = null
    }
}
