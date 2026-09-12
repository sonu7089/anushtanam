package com.mantrasiddhi.data.sync

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/** Foreground change discovery plus deadline-driven retries only while this account has work. */
@Singleton
class CloudSyncCoordinator @Inject constructor(
    private val auth: FirebaseAuth,
    private val dao: CloudSyncDao,
    private val requests: CloudSyncRequests,
    private val scheduler: CloudSyncScheduler,
    private val japaSync: JapaCloudSync,
    private val database: com.mantrasiddhi.data.local.MantraSiddhiDatabase,
    private val discovery: CloudSyncDiscovery,
    private val runner: CloudSyncRunner
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
                    if (account == null) return@collectLatest
                    delay(1500)
                    while (isActive && auth.currentUser?.uid == account) {
                        try {
                            japaSync.requestRecovery(account)
                            discovery.watch(account)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { scheduler.schedule() }
                        delay(CloudSyncRunner.INTERVAL_MS)
                    }
                }
        }
        scope.launch {
            combine(dao.pendingAccounts(), database.backupDao().pendingRestoreAccounts(),
                database.backupDao().changedAccounts(), uid, foreground) {
                uploads, restores, changes, account, visible ->
                account?.takeIf { it in uploads || it in restores || it in changes } to visible
            }.distinctUntilChanged().collectLatest { (account, visible) ->
                if (account == null) { scheduler.cancelIdle(); return@collectLatest }
                scheduler.schedule()
                if (visible) while (isActive && auth.currentUser?.uid == account) {
                    val remaining = try { runner.remainingMs(account) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { CloudSyncRunner.INTERVAL_MS }
                    if (remaining > 0) { delay(remaining.coerceAtMost(CloudSyncRunner.INTERVAL_MS)); continue }
                    if (!dao.hasPending(account) && !japaSync.needsRestore(account) && !japaSync.hasLocalChanges(account)) break
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
