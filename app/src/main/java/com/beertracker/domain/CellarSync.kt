package com.beertracker.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The shared cellar this phone belongs to, as remembered on the phone. */
data class CellarMembership(val cellarId: String, val inviteCode: String)

/** What the cellar record says: its code and how many phones have joined. */
data class CellarInfo(val inviteCode: String, val memberCount: Int)

sealed interface RemoteChange {
    data class Upsert(val beer: TriedBeer) : RemoteChange
    data class Remove(val beerId: String) : RemoteChange
}

/** One listener callback: the document changes since the previous one. */
data class RemoteBeersUpdate(
    val changes: List<RemoteChange>,
    /** False while the snapshot comes from the local cache only. */
    val fromServer: Boolean,
    /** True while this phone has writes the server has not confirmed. */
    val hasPendingWrites: Boolean,
)

sealed class SyncException(message: String) : Exception(message) {
    /** The build has no Firebase configuration. */
    class Unavailable : SyncException("Sync is not configured in this build")
    class Offline : SyncException("Could not reach the server")
    class InvalidCode : SyncException("The code is not eight letters and digits")
    class UnknownCode : SyncException("No cellar has this code")
    class Failed(cause: Throwable) : SyncException(cause.message ?: "Sync failed") {
        init {
            initCause(cause)
        }
    }
}

/**
 * Everything the app needs from Firebase, so the sync engine and the UI
 * never see a Firebase type and can be tested against a fake.
 */
interface CellarRemote {
    val isAvailable: Boolean
    val currentUserId: String?

    /** Anonymous sign-in, or the existing user; returns the user id. */
    suspend fun signIn(): String

    /** Creates the cellar and its invite record; returns the cellar id. */
    suspend fun createCellar(inviteCode: String): String

    /** Resolves the code and adds this user to the cellar; returns the cellar id. */
    suspend fun joinCellar(inviteCode: String): String

    fun observeCellar(cellarId: String): Flow<CellarInfo>

    fun observeBeers(cellarId: String): Flow<RemoteBeersUpdate>

    /** Enqueues and returns at once; the remote delivers it when it can. */
    fun putBeer(cellarId: String, beer: TriedBeer)

    /** Enqueues and returns at once; the remote delivers it when it can. */
    fun deleteBeer(cellarId: String, beerId: String)
}

interface SyncMembershipStore {
    val membership: StateFlow<CellarMembership?>
    fun save(membership: CellarMembership)
    fun clear()
}

sealed interface SyncStatus {
    /** The build has no Firebase configuration. */
    data object Unavailable : SyncStatus

    data object NotPaired : SyncStatus

    data class Paired(
        val inviteCode: String,
        /** Null until the cellar record has been read. */
        val memberCount: Int?,
        /** Null until the first snapshot from the server. */
        val lastSyncedUtc: Long?,
        val hasPendingUploads: Boolean,
    ) : SyncStatus
}
