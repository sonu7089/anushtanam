/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.sandhya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Re-arms all three reminders after a reboot or a clock/timezone change, so a
 * traveller crossing zones gets reminders at the correct local solar moment.
 *
 * Note: while the app is in the force-stopped state (e.g. freshly installed and never
 * opened), the system withholds these broadcasts from manifest receivers. The cold-start
 * re-arm in [com.mantrasiddhi.MantraSiddhiApplication.onCreate] is the backstop that
 * corrects timings the next time the app is opened.
 */
class SandhyaBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val scheduler = SandhyaReceiverEntryPoint.from(context).scheduler()
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        withTimeoutOrNull(WORK_BUDGET_MS) { scheduler.scheduleAll(force = true) }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private companion object {
        const val WORK_BUDGET_MS = 8_000L
    }
}
