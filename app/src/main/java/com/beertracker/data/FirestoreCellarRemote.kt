package com.beertracker.data

import android.content.Context
import android.util.Log
import com.beertracker.domain.CellarInfo
import com.beertracker.domain.CellarRemote
import com.beertracker.domain.RemoteBeerCodec
import com.beertracker.domain.RemoteBeersUpdate
import com.beertracker.domain.RemoteChange
import com.beertracker.domain.SyncException
import com.beertracker.domain.TriedBeer
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * The Firebase side of sync. Firestore's own offline queue carries every
 * put and delete, so those return at once; only pairing waits for the
 * server, with a timeout, because a queued write that never completes
 * means there is no server to talk to.
 */
class FirestoreCellarRemote(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : CellarRemote {

    override val isAvailable: Boolean = true

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override suspend fun signIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = try {
            auth.signInAnonymously().await()
        } catch (error: FirebaseNetworkException) {
            throw SyncException.Offline()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw SyncException.Failed(error)
        }
        return result.user?.uid
            ?: throw SyncException.Failed(IllegalStateException("Anonymous sign-in returned no user"))
    }

    override suspend fun createCellar(inviteCode: String): String {
        val uid = requireUser()
        val cellar = firestore.collection(CELLARS).document()
        val batch = firestore.batch()
        batch.set(
            cellar,
            mapOf(
                FIELD_INVITE_CODE to inviteCode,
                FIELD_MEMBERS to listOf(uid),
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            firestore.collection(INVITES).document(inviteCode),
            mapOf(
                FIELD_CELLAR_ID to cellar.id,
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            ),
        )
        awaitServer { batch.commit().await() }
        return cellar.id
    }

    override suspend fun joinCellar(inviteCode: String): String {
        val uid = requireUser()
        // Source.SERVER so a stale cache can never answer for a code.
        val invite = awaitServer {
            firestore.collection(INVITES).document(inviteCode).get(Source.SERVER).await()
        }
        val cellarId = invite.takeIf { it.exists() }?.getString(FIELD_CELLAR_ID)
            ?: throw SyncException.UnknownCode()
        awaitServer {
            firestore.collection(CELLARS).document(cellarId)
                .update(FIELD_MEMBERS, FieldValue.arrayUnion(uid))
                .await()
        }
        return cellarId
    }

    override fun observeCellar(cellarId: String): Flow<CellarInfo> = callbackFlow {
        val registration = firestore.collection(CELLARS).document(cellarId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(SyncException.Failed(error))
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val inviteCode = snapshot.getString(FIELD_INVITE_CODE) ?: return@addSnapshotListener
                val members = (snapshot.get(FIELD_MEMBERS) as? List<*>)?.size ?: 0
                trySend(CellarInfo(inviteCode, members))
            }
        awaitClose { registration.remove() }
    }

    override fun observeBeers(cellarId: String): Flow<RemoteBeersUpdate> = callbackFlow {
        val registration = beers(cellarId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(SyncException.Failed(error))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val changes = snapshot.documentChanges.mapNotNull { change ->
                    when (change.type) {
                        DocumentChange.Type.REMOVED -> RemoteChange.Remove(change.document.id)
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            val beer = RemoteBeerCodec.fromFields(change.document.id, change.document.data)
                            if (beer == null) {
                                Log.w(TAG, "Skipping a malformed shared beer ${change.document.id}")
                                null
                            } else {
                                RemoteChange.Upsert(beer)
                            }
                        }
                    }
                }
                trySend(
                    RemoteBeersUpdate(
                        changes = changes,
                        fromServer = !snapshot.metadata.isFromCache,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                    ),
                )
            }
        awaitClose { registration.remove() }
    }

    override fun putBeer(cellarId: String, beer: TriedBeer) {
        beers(cellarId).document(beer.id).set(RemoteBeerCodec.toFields(beer))
    }

    override fun deleteBeer(cellarId: String, beerId: String) {
        beers(cellarId).document(beerId).delete()
    }

    private fun beers(cellarId: String) =
        firestore.collection(CELLARS).document(cellarId).collection(BEERS)

    private fun requireUser(): String =
        auth.currentUser?.uid ?: throw SyncException.Failed(IllegalStateException("Not signed in"))

    /**
     * Firestore never fails a queued write while offline, it just waits, so
     * a pairing step that has not completed within the timeout is reported
     * as offline.
     */
    private suspend fun <T> awaitServer(block: suspend () -> T): T = try {
        withTimeout(SERVER_TIMEOUT_MS) { block() }
    } catch (error: TimeoutCancellationException) {
        throw SyncException.Offline()
    } catch (error: FirebaseFirestoreException) {
        if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE) throw SyncException.Offline()
        throw SyncException.Failed(error)
    } catch (error: FirebaseNetworkException) {
        throw SyncException.Offline()
    }

    companion object {
        private const val TAG = "FirestoreCellarRemote"
        private const val CELLARS = "cellars"
        private const val INVITES = "invites"
        private const val BEERS = "beers"
        private const val FIELD_INVITE_CODE = "inviteCode"
        private const val FIELD_MEMBERS = "members"
        private const val FIELD_CELLAR_ID = "cellarId"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val SERVER_TIMEOUT_MS = 20_000L

        /**
         * The Firestore remote when the google-services plugin generated
         * Firebase's resources for this build (FirebaseInitProvider then
         * initialises the default app at process start), otherwise the
         * stand-in that reports sync as unavailable.
         */
        fun create(context: Context): CellarRemote =
            if (FirebaseApp.getApps(context).isEmpty()) {
                UnavailableCellarRemote
            } else {
                FirestoreCellarRemote(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
            }
    }
}

/** A build without Firebase configuration: nothing to sync with. */
object UnavailableCellarRemote : CellarRemote {
    override val isAvailable: Boolean = false
    override val currentUserId: String? = null
    override suspend fun signIn(): String = throw SyncException.Unavailable()
    override suspend fun createCellar(inviteCode: String): String = throw SyncException.Unavailable()
    override suspend fun joinCellar(inviteCode: String): String = throw SyncException.Unavailable()
    override fun observeCellar(cellarId: String): Flow<CellarInfo> = emptyFlow()
    override fun observeBeers(cellarId: String): Flow<RemoteBeersUpdate> = emptyFlow()
    override fun putBeer(cellarId: String, beer: TriedBeer) = Unit
    override fun deleteBeer(cellarId: String, beerId: String) = Unit
}
