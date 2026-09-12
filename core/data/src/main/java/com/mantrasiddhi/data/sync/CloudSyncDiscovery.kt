package com.mantrasiddhi.data.sync

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.entity.BackupQueueState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/** A one-document feed query is only an invalidation signal; restore still validates every record. */
class CloudSyncDiscovery @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val database: MantraSiddhiDatabase,
    private val sync: JapaCloudSync
) {
    internal fun changes(uid: String): Flow<String> = callbackFlow {
        val listener = firestore.collection("users").document(uid).collection("backupChanges")
            .orderBy("changedAt", Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                if (failure != null) close(failure)
                else if (snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) {
                    val latest = snapshot.documents.firstOrNull()
                    val time = latest?.data?.get("changedAt") as? com.google.firebase.Timestamp
                    trySend("${latest?.id.orEmpty()}:${time?.seconds ?: 0}:${time?.nanoseconds ?: 0}")
                }
            }
        awaitClose { listener.remove() }
    }.conflate().distinctUntilChanged()

    /** Caller owns visibility/account lifetime; terminal listener failures are retried with a cap. */
    suspend fun watch(uid: String) {
        changes(uid).retryWhen { failure, attempt ->
            if (failure is CancellationException || auth.currentUser?.uid != uid) false
            else {
                try { sync.markFailure(uid); sync.schedule() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Retry storage along with the listener. */ }
                val ceiling = (60_000L * (1L shl attempt.coerceAtMost(4).toInt())).coerceAtMost(900_000L)
                delay(if (ceiling == 60_000L) ceiling else kotlin.random.Random.nextLong(ceiling / 2, ceiling + 1))
                true
            }
        }.catch { failure ->
            if (failure is CancellationException || auth.currentUser?.uid == uid) throw failure
        }.collect { token ->
            database.withTransaction {
                if (auth.currentUser?.uid != uid) return@withTransaction
                if (database.backupDao().queued(uid, "discovery:seen") != token) {
                    // Persist the request and observed signal atomically. A concurrent pull cannot clear it.
                    sync.requestRecovery(uid)
                    database.backupDao().putQueued(BackupQueueState(uid, "discovery:seen", token))
                }
            }
        }
    }
}
