/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi

import android.app.Application
import com.mantrasiddhi.data.notification.GoalNotifier
import com.mantrasiddhi.data.sync.CloudSyncCoordinator
import com.mantrasiddhi.data.sandhya.SandhyaNotifier
import com.mantrasiddhi.data.sandhya.SandhyaScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MantraSiddhiApplication : Application() {

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        com.mantrasiddhi.domain.diagnostics.WorkProbe.enabled = BuildConfig.PERF_PROBES
    }

    @Inject lateinit var sandhyaNotifier: SandhyaNotifier
    @Inject lateinit var sandhyaScheduler: SandhyaScheduler
    @Inject lateinit var goalNotifier: GoalNotifier
    @Inject lateinit var cloudSyncCoordinator: CloudSyncCoordinator
    @Inject lateinit var teachingNotifications: com.mantrasiddhi.data.messages.TeachingNotifications

    override fun onCreate() {
        super.onCreate()
        teachingNotifications.start()
        cloudSyncCoordinator.start(this)
        // Create the reminder channel up front so users can tune it in system settings.
        sandhyaNotifier.ensureChannel()
        // Create the parent-goal channel up front for the same reason.
        goalNotifier.ensureChannel()
        // Re-arm reminders on cold start — the resilient backstop if alarms were lost
        // (process death, app update). No-ops when the feature is disabled.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching { sandhyaScheduler.scheduleAll() }
        }
    }
}
