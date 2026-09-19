package com.beertracker.data

import android.util.Log
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CellarInfo
import com.beertracker.domain.CellarMembership
import com.beertracker.domain.CellarRemote
import com.beertracker.domain.InviteCodes
import com.beertracker.domain.RemoteChange
import com.beertracker.domain.SyncException
import com.beertracker.domain.SyncMembershipStore
import com.beertracker.domain.SyncStatus
import com.beertracker.domain.TriedBeer
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps this phone's Room database and the shared Firestore cellar in step.
 *
 * Room stays the source of truth for every screen. Local writes arrive
 * through [pushBeer] and [pushDelete] (called by SyncingBeerRepository after
 * the Room write) and go straight to the remote, which queues them itself
 * while offline. Remote changes arrive through the remote's listener and
 * are applied to the Room repository directly, never through
 * SyncingBeerRepository, so nothing that came from Firestore is pushed back.
 */
class CellarSyncEngine(
    private val local: BeerRepository,
    private val remote: CellarRemote,
    private val membershipStore: SyncMembershipStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    /** What the listeners have learned since the current membership started. */
    private data class ListenerState(
        val cellarInfo: CellarInfo? = null,
        val lastSyncedUtc: Long? = null,
        val hasPendingUploads: Boolean = false,
    )

    private val listenerState = MutableStateFlow(ListenerState())
    private var job: Job? = null

    val status: StateFlow<SyncStatus> =
        combine(membershipStore.membership, listenerState) { membership, listener ->
            statusOf(membership, listener)
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            statusOf(membershipStore.membership.value, listenerState.value),
        )

    /** The anonymous user id while paired, else null; stamped onto addedBy. */
    val pairedUserId: String?
        get() = if (membershipStore.membership.value != null) remote.currentUserId else null

    /** Starts following the stored membership; safe to call more than once. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            membershipStore.membership.collectLatest { membership ->
                listenerState.value = ListenerState()
                if (membership == null || !remote.isAvailable) return@collectLatest
                coroutineScope {
                    launch { followCellar(membership.cellarId) }
                    launch { followBeers(membership.cellarId) }
                }
            }
        }
    }

    suspend fun createCellar() {
        requireAvailable()
        remote.signIn()
        val inviteCode = InviteCodes.generate(random)
        val cellarId = remote.createCellar(inviteCode)
        pair(CellarMembership(cellarId, inviteCode))
    }

    suspend fun joinCellar(input: String) {
        requireAvailable()
        val inviteCode = InviteCodes.normalize(input) ?: throw SyncException.InvalidCode()
        remote.signIn()
        val cellarId = remote.joinCellar(inviteCode)
        pair(CellarMembership(cellarId, inviteCode))
    }

    /** Forgets the pairing on this phone only; the beers stay in Room. */
    fun stopSyncing() = membershipStore.clear()

    fun pushBeer(beer: TriedBeer) {
        val membership = membershipStore.membership.value ?: return
        remote.putBeer(membership.cellarId, beer)
    }

    fun pushDelete(beerId: String) {
        val membership = membershipStore.membership.value ?: return
        remote.deleteBeer(membership.cellarId, beerId)
    }

    /**
     * Saving the membership starts the listeners; then everything already
     * here goes up. Not cancellable: once the membership is saved, the
     * upload must finish even if the user leaves the screen, or the phone
     * would be paired with none of its beers in the cellar.
     */
    private suspend fun pair(membership: CellarMembership) = withContext(NonCancellable) {
        membershipStore.save(membership)
        local.observeBeers().first().forEach { remote.putBeer(membership.cellarId, it) }
    }

    private suspend fun followCellar(cellarId: String) {
        remote.observeCellar(cellarId)
            .retryAfterFailure("cellar")
            .collect { info -> listenerState.update { it.copy(cellarInfo = info) } }
    }

    private suspend fun followBeers(cellarId: String) {
        remote.observeBeers(cellarId)
            .retryAfterFailure("beers")
            .collect { update ->
                update.changes.forEach { change ->
                    try {
                        apply(change)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Could not apply a shared change, skipping it", error)
                    }
                }
                listenerState.update {
                    it.copy(
                        lastSyncedUtc = if (update.fromServer) clock() else it.lastSyncedUtc,
                        hasPendingUploads = update.hasPendingWrites,
                    )
                }
            }
    }

    private suspend fun apply(change: RemoteChange) {
        when (change) {
            is RemoteChange.Upsert -> {
                val existing = local.getBeer(change.beer.id)
                if (existing == null) {
                    local.addBeer(change.beer)
                } else {
                    // The photo lives only on this phone; the shared record never carries it.
                    local.updateBeer(change.beer.copy(photoUri = existing.photoUri))
                }
            }
            // A Remove is authoritative. Today the rules make a non-member's
            // listener fail before any rollback could reach it, so REMOVED
            // can only mean the other phone deleted the beer. If the rules
            // ever validate beer fields, a rejected local write would also
            // arrive here as REMOVED and delete the beer and its photo;
            // revisit this before adding such rules.
            is RemoteChange.Remove -> local.deleteBeer(change.beerId)
        }
    }

    /**
     * A listener that fails (for example a rules change denying access) is
     * logged and restarted after a pause, rather than left dead until the
     * next app launch.
     */
    private fun <T> Flow<T>.retryAfterFailure(what: String): Flow<T> = retryWhen { error, _ ->
        if (error is CancellationException) throw error
        Log.w(TAG, "Sync listener for $what failed, retrying in ${RETRY_DELAY_MS / 1000} s", error)
        delay(RETRY_DELAY_MS)
        true
    }

    private fun requireAvailable() {
        if (!remote.isAvailable) throw SyncException.Unavailable()
    }

    private fun statusOf(membership: CellarMembership?, listener: ListenerState): SyncStatus = when {
        !remote.isAvailable -> SyncStatus.Unavailable
        membership == null -> SyncStatus.NotPaired
        else -> SyncStatus.Paired(
            inviteCode = membership.inviteCode,
            memberCount = listener.cellarInfo?.memberCount,
            lastSyncedUtc = listener.lastSyncedUtc,
            hasPendingUploads = listener.hasPendingUploads,
        )
    }

    private companion object {
        const val TAG = "CellarSyncEngine"
        const val RETRY_DELAY_MS = 30_000L
    }
}
