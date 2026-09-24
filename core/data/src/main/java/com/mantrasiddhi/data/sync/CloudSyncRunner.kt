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

open class CloudSyncNetwork @Inject constructor(@ApplicationContext private val context: Context) {
    open fun isConnected(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

open class CloudSyncClock @Inject constructor(@ApplicationContext private val context: Context) {
    open fun nowMs(): Long = System.currentTimeMillis()
    open fun elapsedMs(): Long = android.os.SystemClock.elapsedRealtime()
    open fun bootCount(): Long = android.provider.Settings.Global.getInt(context.contentResolver,
        android.provider.Settings.Global.BOOT_COUNT, -1).toLong()
}

@Singleton
class CloudSyncRunner @Inject constructor(
    private val dao: CloudSyncDao,
    private val auth: FirebaseAuth,
    private val network: CloudSyncNetwork,
    private val transport: CloudSyncTransport,
    private val gate: CloudSyncRetryGate,
    private val japaSync: JapaCloudSync
) {
    private val lock = Mutex()

    private val _activeUid = MutableStateFlow<String?>(null)
    val activeUid = _activeUid.asStateFlow()

    suspend fun runPending(): Boolean = run(manual = false, expectedUid = auth.currentUser?.uid)
    suspend fun syncNow(uid: String): Boolean = run(manual = true, expectedUid = uid)

    /** Concurrent triggers do not queue more network attempts behind the active one. */
    private suspend fun run(manual: Boolean, expectedUid: String?): Boolean {
        if (!lock.tryLock()) {
            // A Settings request may overlap a persistent background job.
            if (manual && expectedUid != null && auth.currentUser?.uid == expectedUid)
                japaSync.requestRecovery(expectedUid)
            return true
        }
        try {
            val uid = auth.currentUser?.uid ?: return false
            if (uid != expectedUid) return false
            try {
                val claimed = gate.claim(uid, manual)
                // An idle manual pull is durable even when the shared gate delays it.
                if (manual) japaSync.requestRecovery(uid)
                if (!claimed) return true
                _activeUid.value = uid
                japaSync.capture(uid)
                if (auth.currentUser?.uid != uid) return false
                val pending = dao.hasPending(uid)
                if (!pending && !japaSync.needsRestore(uid)) { gate.succeeded(uid); return japaSync.hasLocalChanges(uid) }
                if (!network.isConnected()) return true
                if (pending) {
                    // Persist the follow-up before any acknowledgement can empty the queue.
                    japaSync.markRestorePending(uid)
                    val batch = dao.pending(uid)
                    if (batch.isNotEmpty() && auth.currentUser?.uid == uid) {
                        val acknowledged = withTimeoutOrNull(20_000L) {
                            val backupKinds = setOf(SyncKind.JAPA_ENTRY, SyncKind.JAPA_GOAL,
                                SyncKind.PRACTICE_RECORD, SyncKind.CONFLICT_RESOLUTION, SyncKind.ACCOUNT_RECORD)
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
                        if (!acknowledged) { failed(uid); return true }
                    }
                }
                if (!dao.hasPending(uid) && auth.currentUser?.uid == uid) {
                    val restored = withTimeoutOrNull(45_000L) { japaSync.restore(uid); true } == true
                    if (!restored) { failed(uid); return true }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (auth.currentUser?.uid != uid) return false
                failed(uid)
                // Backend exceptions can contain account IDs and document paths.
                Log.w("CloudSync", "Pending cloud changes retained for retry")
                return auth.currentUser?.uid == uid
            }
            gate.succeeded(uid)
            return auth.currentUser?.uid == uid && (dao.hasPending(uid) || japaSync.needsRestore(uid) || japaSync.hasLocalChanges(uid))
        } finally {
            _activeUid.value = null
            lock.unlock()
        }
    }

    private suspend fun failed(uid: String) {
        try { gate.failed(uid) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* The in-process emergency deadline still bounds storage failures. */ }
        japaSync.markFailure(uid)
    }

    suspend fun remainingMs(uid: String, manual: Boolean = false) = gate.remainingMs(uid, manual)

    companion object { const val INTERVAL_MS = 60_000L }
}
