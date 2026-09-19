package com.beertracker

import com.beertracker.domain.CellarInfo
import com.beertracker.domain.CellarRemote
import com.beertracker.domain.RemoteBeersUpdate
import com.beertracker.domain.SyncException
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Stands in for Firebase. Cellars, invites and every put or delete are
 * recorded in memory; remote changes are fed in by the test through
 * [emitBeers] and [cellarInfo].
 */
class FakeCellarRemote(override var isAvailable: Boolean = true) : CellarRemote {

    var userId: String? = null
    /** Thrown by [signIn] when set; a SyncException or any other Throwable. */
    var signInError: Throwable? = null
    /** When set, [signIn] waits on it, so a test can observe the working state. */
    var signInGate: CompletableDeferred<Unit>? = null
    var signIns = 0
    var nextCellarId = "cellar-1"

    /** Cellar id to member ids. */
    val cellars = mutableMapOf<String, MutableList<String>>()
    /** Invite code to cellar id. */
    val invites = mutableMapOf<String, String>()
    val puts = mutableListOf<Pair<String, TriedBeer>>()
    val deletes = mutableListOf<Pair<String, String>>()

    var beersSubscriptions = 0
    /** How many upcoming [observeBeers] subscriptions fail straight away. */
    var beersFailuresLeft = 0
    val cellarInfo = MutableStateFlow<CellarInfo?>(null)
    private val beerUpdates = MutableSharedFlow<Pair<String, RemoteBeersUpdate>>()

    override val currentUserId: String?
        get() = userId

    override suspend fun signIn(): String {
        signIns++
        signInGate?.await()
        signInError?.let { throw it }
        return userId ?: "user-1".also { userId = it }
    }

    override suspend fun createCellar(inviteCode: String): String {
        val uid = userId ?: throw SyncException.Failed(IllegalStateException("not signed in"))
        val id = nextCellarId
        cellars[id] = mutableListOf(uid)
        invites[inviteCode] = id
        return id
    }

    override suspend fun joinCellar(inviteCode: String): String {
        val uid = userId ?: throw SyncException.Failed(IllegalStateException("not signed in"))
        val id = invites[inviteCode] ?: throw SyncException.UnknownCode()
        cellars.getValue(id).let { members -> if (uid !in members) members.add(uid) }
        return id
    }

    override fun observeCellar(cellarId: String): Flow<CellarInfo> = cellarInfo.filterNotNull()

    override fun observeBeers(cellarId: String): Flow<RemoteBeersUpdate> = flow {
        beersSubscriptions++
        if (beersFailuresLeft > 0) {
            beersFailuresLeft--
            throw IllegalStateException("listener denied")
        }
        emitAll(beerUpdates.filter { it.first == cellarId }.map { it.second })
    }

    override fun putBeer(cellarId: String, beer: TriedBeer) {
        puts += cellarId to beer
    }

    override fun deleteBeer(cellarId: String, beerId: String) {
        deletes += cellarId to beerId
    }

    /** Delivers one listener callback to whoever observes [cellarId]. */
    suspend fun emitBeers(cellarId: String, update: RemoteBeersUpdate) {
        beerUpdates.emit(cellarId to update)
    }
}
