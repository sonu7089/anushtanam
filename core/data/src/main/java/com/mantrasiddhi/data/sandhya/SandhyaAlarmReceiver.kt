package com.mantrasiddhi.data.sandhya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.util.SandhyaDateKey
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class SandhyaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val period = runCatching { SandhyaPeriod.valueOf(intent.getStringExtra(SandhyaContract.EXTRA_PERIOD) ?: "") }.getOrNull() ?: return
        val stage = runCatching { SandhyaReminderStage.valueOf(intent.getStringExtra(SandhyaContract.EXTRA_STAGE) ?: "") }.getOrNull() ?: return
        val event = intent.getLongExtra(SandhyaContract.EXTRA_EVENT_MILLIS, 0)
        val owner = intent.getLongExtra(SandhyaContract.EXTRA_USER_ID, 0)
        val date = intent.getStringExtra(SandhyaContract.EXTRA_DATE_KEY) ?: return
        val entry = SandhyaReceiverEntryPoint.from(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { entry.scheduler().scheduleNext(period, stage, event) }
                    .onFailure { Log.w("SandhyaReminder", "Rearm failed", it) }
                withTimeoutOrNull(8_000) {
                    deliveryLock.withLock {
                        val settings = entry.preferences().current()
                        val now = System.currentTimeMillis()
                        Log.d("SandhyaReminder", "received $period $stage event=$event at=$now")
                        if (!settings.enabled || owner <= 0 || settings.userId != owner || !settings.stageEnabled(stage)) return@withLock
                        if (intent.getStringExtra(SandhyaContract.EXTRA_REGION) != settings.region.toString() ||
                            intent.getStringExtra(SandhyaContract.EXTRA_ZONE) != settings.zone().id) return@withLock
                        if (date != SandhyaDateKey.of(LocalDate.now(settings.zone()))) return@withLock
                        val actual = SandhyaReminderPolicy.delivery(stage, event, now, settings.eventEnabled) ?: return@withLock
                        if (entry.preferences().deliveredDate(owner, period, actual) == date) return@withLock
                        if (actual == SandhyaReminderStage.PREPARATION && entry.preferences().deliveredDate(owner, period, SandhyaReminderStage.EVENT) == date) return@withLock
                        if (entry.logRepository().getLogs(owner).any { it.dateKey == date && it.period == period && it.status == SandhyaStatus.OBSERVED }) return@withLock
                        val posted = entry.notifier().showReminder(period, event, settings.zone(), date, owner, actual)
                        Log.d("SandhyaReminder", "post $period $actual accepted=$posted")
                        if (posted) {
                            entry.preferences().recordDelivery(owner, period, actual, event, date)
                            entry.logRepository().logNotified(owner, period, event, date)
                        }
                    }
                }
            } catch (error: Exception) { Log.w("SandhyaReminder", "Delivery failed", error) }
            finally { pending.finish() }
        }
    }
    companion object { private val deliveryLock = Mutex() }
}
