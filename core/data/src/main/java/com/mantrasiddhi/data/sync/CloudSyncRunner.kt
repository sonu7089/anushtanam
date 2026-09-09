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
import kotlinx.coroutines.sync.withLock
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

    /** Returns whether the current account still has pending work. */
    suspend fun runPending(): Boolean = lock.withLock {
        val uid = auth.currentUser?.uid ?: return@withLock false
        if (!dao.hasPending(uid)) return@withLock false
        val now = clock.nowMs()
        if (dao.claim(uid, now, now + INTERVAL_MS) == 0) return@withLock true
        if (!network.isConnected()) return@withLock true
        try {
            val batch = dao.pending(uid)
            if (batch.isNotEmpty() && auth.currentUser?.uid == uid && network.isConnected()) {
                val acknowledged = withTimeoutOrNull(20_000L) { transport.deliver(batch); true } == true
                if (acknowledged) {
                    dao.acknowledgeBatch(batch.map { it.id })
                    withTimeoutOrNull(20_000L) { japaSync.restore(uid) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("CloudSync", "Pending cloud changes retained for retry", failure)
        }
        auth.currentUser?.uid == uid && dao.hasPending(uid)
    }

    companion object { const val INTERVAL_MS = 60_000L }
}
