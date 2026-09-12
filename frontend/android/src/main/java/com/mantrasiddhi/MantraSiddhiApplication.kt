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
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MantraSiddhiApplication : Application() {

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        com.mantrasiddhi.domain.diagnostics.WorkProbe.enabled = BuildConfig.PERF_PROBES
    }

    @Inject lateinit var sandhyaNotifier: SandhyaNotifier
    @Inject lateinit var goalNotifier: GoalNotifier
    @Inject lateinit var cloudSyncCoordinator: CloudSyncCoordinator
    @Inject lateinit var teachingNotifications: com.mantrasiddhi.data.messages.TeachingNotifications

    @Inject lateinit var calendarTime: com.mantrasiddhi.domain.util.CalendarTime

    override fun onCreate() {
        super.onCreate()
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                calendarTime.invalidate()
            }
        }, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
        }, android.content.Context.RECEIVER_NOT_EXPORTED)
        teachingNotifications.start()
        cloudSyncCoordinator.start(this)
        // Create the reminder channel up front so users can tune it in system settings.
        sandhyaNotifier.ensureChannel()
        // Create the parent-goal channel up front for the same reason.
        goalNotifier.ensureChannel()
        // Profile opening requests one background reminder re-arm after identity checks.
    }
}
