package com.mantrasiddhi.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

class CloudSyncNetwork @Inject constructor(@ApplicationContext private val context: Context) {
    fun isConnected(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

class CloudSyncClock @Inject constructor() { fun nowMs(): Long = System.currentTimeMillis() }

@Singleton
class CloudSyncRunner @Inject constructor(
    private val dao: CloudSyncDao,
    private val auth: FirebaseAuth,
    private val network: CloudSyncNetwork,
    private val transport: CloudSyncTransport,
    private val clock: CloudSyncClock,
    private val japaSync: JapaCloudSync
) {
    private val lock = Mutex()

    private val _activeUid = MutableStateFlow<String?>(null)
    val activeUid = _activeUid.asStateFlow()

    suspend fun runPending(): Boolean = run(manual = false, expectedUid = auth.currentUser?.uid)
    suspend fun syncNow(uid: String): Boolean = run(manual = true, expectedUid = uid)

    /** Concurrent triggers do not queue more network attempts behind the active one. */
    private suspend fun run(manual: Boolean, expectedUid: String?): Boolean {
        if (!lock.tryLock()) return true
        try {
            val uid = auth.currentUser?.uid ?: return false
            if (uid != expectedUid) return false
            _activeUid.value = uid
            japaSync.capture(uid)
            val pending = dao.hasPending(uid)
            if (!pending && !japaSync.needsRestore(uid)) return false
            val now = clock.nowMs()
            if (!dao.claimSyncAttempt(uid, now, manual)) return true
            if (!network.isConnected()) return true
            try {
                if (pending) {
                    // Persist the follow-up before any acknowledgement can empty the queue.
                    japaSync.markRestorePending(uid)
                    val batch = dao.pending(uid)
                    if (batch.isNotEmpty() && auth.currentUser?.uid == uid) {
                        val acknowledged = withTimeoutOrNull(20_000L) {
                            val backupKinds = setOf(SyncKind.JAPA_ENTRY, SyncKind.JAPA_GOAL,
                                SyncKind.PRACTICE_RECORD, SyncKind.CONFLICT_RESOLUTION)
                            val legacy = batch.filter { it.kind !in backupKinds }
                            if (legacy.isNotEmpty()) {
                                transport.deliver(legacy)
                                dao.acknowledgeBatch(legacy.map { it.id })
                            }
                            // A slow connection can still make durable progress through a large history.
                            for (event in batch.filter { it.kind in backupKinds }) {
                                transport.deliver(listOf(event))
                                dao.acknowledgeBatch(listOf(event.id))
                            }
                            true
                        } == true
                        if (!acknowledged) { japaSync.markFailure(uid); return true }
                    }
                }
                if (!dao.hasPending(uid) && auth.currentUser?.uid == uid) {
                    val restored = withTimeoutOrNull(45_000L) { japaSync.restore(uid); true } == true
                    if (!restored) { japaSync.markFailure(uid); return true }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                japaSync.markFailure(uid)
                Log.w("CloudSync", "Pending cloud changes retained for retry", failure)
                return auth.currentUser?.uid == uid
            }
            return auth.currentUser?.uid == uid && (dao.hasPending(uid) || japaSync.needsRestore(uid))
        } finally {
            _activeUid.value = null
            lock.unlock()
        }
    }

    companion object { const val INTERVAL_MS = 60_000L }
}
