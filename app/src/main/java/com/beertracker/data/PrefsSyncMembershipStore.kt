package com.beertracker.data

import android.content.Context
import com.beertracker.domain.CellarMembership
import com.beertracker.domain.SyncMembershipStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences-backed, two small strings, so no DataStore. Both keys
 * present means paired; anything else means not paired.
 */
class PrefsSyncMembershipStore(context: Context) : SyncMembershipStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _membership = MutableStateFlow(load())
    override val membership: StateFlow<CellarMembership?> = _membership.asStateFlow()

    override fun save(membership: CellarMembership) {
        prefs.edit()
            .putString(KEY_CELLAR_ID, membership.cellarId)
            .putString(KEY_INVITE_CODE, membership.inviteCode)
            .apply()
        _membership.value = membership
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_CELLAR_ID)
            .remove(KEY_INVITE_CODE)
            .apply()
        _membership.value = null
    }

    private fun load(): CellarMembership? {
        val cellarId = prefs.getString(KEY_CELLAR_ID, null) ?: return null
        val inviteCode = prefs.getString(KEY_INVITE_CODE, null) ?: return null
        return CellarMembership(cellarId, inviteCode)
    }

    private companion object {
        const val PREFS_NAME = "sync"
        const val KEY_CELLAR_ID = "cellar_id"
        const val KEY_INVITE_CODE = "invite_code"
    }
}
