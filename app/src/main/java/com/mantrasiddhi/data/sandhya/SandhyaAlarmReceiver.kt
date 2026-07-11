/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.sandhya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.util.SandhyaDateKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fired ~5 minutes before a solar event. Logs the occurrence, posts the reminder,
 * then re-arms the same period for the next day. [goAsync] keeps the process alive
 * long enough for the IO work to finish.
 */
class SandhyaAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val period = intent.getStringExtra(SandhyaContract.EXTRA_PERIOD)
            ?.let { runCatching { SandhyaPeriod.valueOf(it) }.getOrNull() } ?: return
        val eventMillis = intent.getLongExtra(SandhyaContract.EXTRA_EVENT_MILLIS, 0L)
        val extraDateKey = intent.getStringExtra(SandhyaContract.EXTRA_DATE_KEY)
        val entryPoint = SandhyaReceiverEntryPoint.from(context)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Durability-critical and cheap: re-arm the next occurrence FIRST, so the
                // daily self-perpetuating chain survives even if the work below is slow or
                // cancelled. Losing this would stop all future reminders until app open.
                runCatching { entryPoint.scheduler().scheduleNext(period) }

                withTimeoutOrNull(WORK_BUDGET_MS) {
                    val settings = entryPoint.preferences().current()
                    if (!settings.enabled) return@withTimeoutOrNull
                    val zone = settings.zone()
                    val dateKey = extraDateKey ?: SandhyaDateKey.of(
                        if (eventMillis > 0L) eventMillis else System.currentTimeMillis(),
                        zone
                    )
                    // Only attribute a log once a real profile is resolved; never create
                    // orphan rows under the userId=0 default.
                    if (settings.userId > 0L) {
                        entryPoint.logRepository()
                            .logNotified(settings.userId, period, eventMillis, dateKey)
                    }
                    entryPoint.notifier().showReminder(period, eventMillis, zone, dateKey)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val WORK_BUDGET_MS = 8_000L
    }
}
