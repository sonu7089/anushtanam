package com.mantrasiddhi.data.sync

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/** Requests outlive the Settings page. The persistent job covers process removal. */
@Singleton
class CloudSyncRequests @Inject constructor(
    private val runner: CloudSyncRunner,
    private val scheduler: CloudSyncScheduler,
    private val sync: JapaCloudSync,
    private val auth: FirebaseAuth
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var active: Job? = null
    val activeUid = runner.activeUid

    @Synchronized
    fun request(uid: String, manual: Boolean = false): Job {
        active?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            if (auth.currentUser?.uid != uid) return@launch
            try {
                val more = if (manual) runner.syncNow(uid) else runner.runPending()
                if (more) scheduler.schedule()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { sync.markFailure(uid); scheduler.schedule() }
        }.also { active = it }
    }
}
