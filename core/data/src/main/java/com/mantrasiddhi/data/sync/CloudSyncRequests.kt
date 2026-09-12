package com.mantrasiddhi.data.sync

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import javax.inject.Inject
import javax.inject.Singleton

/** Requests outlive the Settings page. The persistent job covers process removal. */
@Singleton
class CloudSyncRequests @Inject constructor(
    private val runner: CloudSyncRunner,
    private val scheduler: CloudSyncScheduler,
    private val sync: JapaCloudSync,
    private val auth: FirebaseAuth,
    private val dao: CloudSyncDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var active: Job? = null
    private var account: String? = null
    private var repeatRequested = false
    private var manualPending = false
    val activeUid = runner.activeUid

    @OptIn(ExperimentalCoroutinesApi::class)
    fun manualReady(uid: String): Flow<Boolean> = dao.observeRetryTiming(uid).flatMapLatest {
        flow {
            var remaining = runner.remainingMs(uid, manual = true)
            if (remaining > 0) emit(false)
            while (remaining > 0) {
                delay(remaining.coerceAtMost(60_000L))
                remaining = runner.remainingMs(uid, manual = true)
            }
            emit(true)
        }
    }

    @Synchronized
    fun request(uid: String, manual: Boolean = false): Job {
        val previous = active?.takeIf { it.isActive }
        if (account == uid && previous != null) {
            repeatRequested = true
            manualPending = manualPending || manual
            return previous
        }
        previous?.cancel()
        account = uid
        repeatRequested = false
        manualPending = false
        return scope.launch {
            previous?.join()
            if (auth.currentUser?.uid != uid) return@launch
            try {
                var useManual = manual
                do {
                    val more = if (useManual) runner.syncNow(uid) else runner.runPending()
                    if (more) scheduler.schedule()
                    val again = synchronized(this@CloudSyncRequests) {
                        if (account != uid) false else {
                            useManual = manualPending
                            manualPending = false
                            repeatRequested.also { repeatRequested = false; if (!it) active = null }
                        }
                    }
                } while (again && auth.currentUser?.uid == uid)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                try { sync.markFailure(uid) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Retain the scheduled retry even when status storage is unavailable. */ }
                finally { scheduler.schedule() }
            }
        }.also { active = it }
    }
}
