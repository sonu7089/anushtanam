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
    private val runner: CloudSyncRunner,
    private val scheduler: CloudSyncScheduler,
    private val japaSync: JapaCloudSync
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
        scope.launch {
            combine(uid, foreground) { account, visible -> account.takeIf { visible } }
                .distinctUntilChanged().collectLatest { account ->
                    if (account != null) {
                        // Profile association may still be finishing when auth first emits.
                        delay(1500)
                        runCatching { withTimeout(20_000) { japaSync.restore(account) } }
                    }
                }
        }
        scope.launch {
            combine(dao.pendingAccounts(), uid, foreground) { pending, account, visible ->
                (account != null && account in pending) to visible
            }.distinctUntilChanged().collectLatest { (pending, visible) ->
                if (!pending) { scheduler.cancelIdle(); return@collectLatest }
                scheduler.schedule()
                if (visible) while (isActive) {
                    val account = auth.currentUser?.uid ?: break
                    val due = dao.nextAttemptAt(account) ?: break
                    delay((due - System.currentTimeMillis()).coerceIn(1L, CloudSyncRunner.INTERVAL_MS))
                    try { runner.runPending() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        Log.w("CloudSync", "Sync check will retry in one minute", failure)
                        delay(CloudSyncRunner.INTERVAL_MS)
                    }
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
