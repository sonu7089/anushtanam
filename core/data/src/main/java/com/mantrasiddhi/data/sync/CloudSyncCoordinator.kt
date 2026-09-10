package com.mantrasiddhi.data.sync

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/** Foreground one-minute polling; no timer or network requests when this account has no work. */
@Singleton
class CloudSyncCoordinator @Inject constructor(
    private val auth: FirebaseAuth,
    private val dao: CloudSyncDao,
    private val requests: CloudSyncRequests,
    private val scheduler: CloudSyncScheduler,
    private val japaSync: JapaCloudSync,
    private val database: com.mantrasiddhi.data.local.MantraSiddhiDatabase
) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val foreground = MutableStateFlow(false)
    private val uid = MutableStateFlow(auth.currentUser?.uid)
    private var activities = 0
    private var started = false

    fun start(application: Application) {
        if (started) return
        started = true
        application.registerActivityLifecycleCallbacks(this)
        auth.addAuthStateListener { uid.value = it.currentUser?.uid }
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
        database.invalidationTracker.addObserver(object : androidx.room.InvalidationTracker.Observer(
            "recitation_sessions", "sandhya_logs", "sandhya_count_changes", "japa_entries", "targets", "purashcharana") {
            override fun onInvalidated(tables: Set<String>) { changes.tryEmit(Unit) }
        })
        scope.launch {
            @OptIn(FlowPreview::class)
            changes.debounce(750).collect {
                val account = auth.currentUser?.uid ?: return@collect
                try { japaSync.capture(account) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { japaSync.markFailure(account); scheduler.schedule() }
            }
        }
        scope.launch {
            combine(uid, foreground) { account, visible -> account.takeIf { visible } }
                .distinctUntilChanged().collectLatest { account ->
                    if (account == null) return@collectLatest
                    delay(1500)
                    try {
                        // Local recovery after process removal. Neither call contacts Firebase.
                        japaSync.capture(account)
                        japaSync.requestRecovery(account)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { japaSync.markFailure(account); scheduler.schedule() }
                }
        }
        scope.launch {
            combine(dao.pendingAccounts(), database.backupDao().pendingRestoreAccounts(), uid, foreground) {
                uploads, restores, account, visible ->
                account?.takeIf { it in uploads || it in restores } to visible
            }.distinctUntilChanged().collectLatest { (account, visible) ->
                if (account == null) { scheduler.cancelIdle(); return@collectLatest }
                scheduler.schedule()
                if (visible) while (isActive && auth.currentUser?.uid == account) {
                    val due = dao.nextAttemptAt(account) ?: 0L
                    delay((due - System.currentTimeMillis()).coerceIn(1L, CloudSyncRunner.INTERVAL_MS))
                    if (!dao.hasPending(account) && !japaSync.needsRestore(account)) break
                    // Leaving the app cancels this wait, not the actual sync request.
                    requests.request(account).join()
                }
            }
        }
    }

    override fun onActivityStarted(activity: Activity) { activities++; foreground.value = true }
    override fun onActivityStopped(activity: Activity) { activities--; foreground.value = activities > 0 }
    override fun onActivityCreated(activity: Activity, state: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
