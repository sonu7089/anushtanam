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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles the "Mark as done" notification action: records the occurrence as
 * OBSERVED and dismisses the notification.
 */
class SandhyaActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SandhyaContract.ACTION_MARK_OBSERVED) return
        val period = intent.getStringExtra(SandhyaContract.EXTRA_PERIOD)
            ?.let { runCatching { SandhyaPeriod.valueOf(it) }.getOrNull() } ?: return
        val dateKey = intent.getStringExtra(SandhyaContract.EXTRA_DATE_KEY) ?: return
        val eventMillis = intent.getLongExtra(SandhyaContract.EXTRA_EVENT_MILLIS, 0L)
        val entryPoint = SandhyaReceiverEntryPoint.from(context)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(WORK_BUDGET_MS) {
                    val userId = entryPoint.preferences().current().userId
                    val repo = entryPoint.logRepository()
                    // Upsert: ensure the row exists (no-op if it does) before flipping it to
                    // OBSERVED, so the tap is never lost even if the NOTIFIED row is missing.
                    repo.logNotified(userId, period, eventMillis, dateKey)
                    repo.markObserved(userId, period, dateKey, System.currentTimeMillis())
                    entryPoint.notifier().cancel(period)
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
