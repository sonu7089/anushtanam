package com.mantrasiddhi.data.sandhya

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.data.preferences.SandhyaSettings
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.SandhyaLogRepository
import com.mantrasiddhi.domain.solar.SolarCalculator
import com.mantrasiddhi.domain.util.SandhyaDateKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SandhyaScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: SandhyaPreferences,
    private val logs: SandhyaLogRepository
) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val lock = Mutex()

    private val startupUser = MutableStateFlow<Long?>(null)
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Process-owned: leaving the entry screen cannot cancel reminder re-arming.
        // Account replacements cancel stale work; the lock guards the current owner check.
        startupScope.launch {
            startupUser.collectLatest { userId ->
                if (userId == null) return@collectLatest
                try {
                    com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("reminder.startup.rearm")
                    scheduleFor(userId)
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    startupUser.compareAndSet(userId, null)
                    Log.w("SandhyaReminder", "Startup re-arm failed", failure)
                }
            }
        }
    }

    fun requestStartupRearm(userId: Long) { startupUser.value = userId }

    suspend fun scheduleAll() = scheduleFor(null)

    private suspend fun scheduleFor(expectedUserId: Long?) = lock.withLock {
        val settings = preferences.current()
        if (expectedUserId != null && settings.userId != expectedUserId) return@withLock
        SandhyaPeriod.entries.forEach { period ->
            // Remove pre-MF-06 alarms, whose identity had no stage.
            PendingIntent.getBroadcast(context, period.requestCode, Intent(context, SandhyaAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
            SandhyaReminderStage.entries.forEach { schedule(period, it, settings) }
        }
    }

    suspend fun scheduleNext(period: SandhyaPeriod, stage: SandhyaReminderStage, afterEvent: Long) = lock.withLock {
        schedule(period, stage, preferences.current(), afterEvent)
    }

    fun cancelAll() {
        startupUser.value = null
        SandhyaPeriod.entries.forEach { period ->
            PendingIntent.getBroadcast(context, period.requestCode, Intent(context, SandhyaAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
            SandhyaReminderStage.entries.forEach { cancel(period, it) }
        }
    }

    suspend fun upcoming(settings: SandhyaSettings, now: Long = System.currentTimeMillis()): Map<Pair<SandhyaPeriod, SandhyaReminderStage>, SandhyaReminderTime> = buildMap {
        for (period in SandhyaPeriod.entries) for (stage in SandhyaReminderStage.entries) {
            next(period, stage, settings, now)?.let { put(period to stage, it) }
        }
    }

    private suspend fun next(period: SandhyaPeriod, stage: SandhyaReminderStage, settings: SandhyaSettings,
        now: Long, afterEvent: Long = 0): SandhyaReminderTime? {
        if (!settings.enabled || settings.userId <= 0 || !settings.stageEnabled(stage)) return null
        val delivered = maxOf(afterEvent, preferences.delivered(settings.userId, period, stage))
        val deliveredDate = preferences.deliveredDate(settings.userId, period, stage)
        val observed = logs.getLogs(settings.userId).filter { it.period == period && it.status == SandhyaStatus.OBSERVED }.map { it.dateKey }.toSet()
        var date = java.time.Instant.ofEpochMilli(now).atZone(settings.zone()).toLocalDate()
        repeat(8) {
            val epochs = SolarCalculator.computeEpochMillis(date, settings.region.latitude, settings.region.longitude, settings.zone())
            val event = when (period) {
                SandhyaPeriod.PRATAH -> epochs.sunriseUtcMillis
                SandhyaPeriod.MADHYAHNA -> epochs.solarNoonUtcMillis
                SandhyaPeriod.SAYAM -> epochs.sunsetUtcMillis
            }
            if (event != null && event > delivered && SandhyaDateKey.of(date) != deliveredDate && SandhyaDateKey.of(date) !in observed) {
                SandhyaReminderPolicy.trigger(event, stage, now)?.let { return SandhyaReminderTime(event, it) }
            }
            date = date.plusDays(1)
        }
        return null
    }

    private suspend fun schedule(period: SandhyaPeriod, stage: SandhyaReminderStage, settings: SandhyaSettings, afterEvent: Long = 0) {
        val next = next(period, stage, settings, System.currentTimeMillis(), afterEvent)
        currentCoroutineContext().ensureActive()
        // A sign-out/account switch may have happened during the log query.
        if (preferences.current().userId != settings.userId) return
        if (next == null) { cancel(period, stage); return }
        val intent = alarmIntent(period, stage).apply {
            putExtra(SandhyaContract.EXTRA_PERIOD, period.name)
            putExtra(SandhyaContract.EXTRA_STAGE, stage.name)
            putExtra(SandhyaContract.EXTRA_USER_ID, settings.userId)
            putExtra(SandhyaContract.EXTRA_EVENT_MILLIS, next.eventMillis)
            putExtra(SandhyaContract.EXTRA_DATE_KEY, SandhyaDateKey.of(next.eventMillis, settings.zone()))
            putExtra(SandhyaContract.EXTRA_REGION, settings.region.toString())
            putExtra(SandhyaContract.EXTRA_ZONE, settings.zone().id)
        }
        val pending = PendingIntent.getBroadcast(context, period.requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerMillis, pending)
        Log.d("SandhyaReminder", "scheduled $period $stage at ${next.triggerMillis}")
    }
    private fun alarmIntent(period: SandhyaPeriod, stage: SandhyaReminderStage) =
        Intent(context, SandhyaAlarmReceiver::class.java).setAction("sandhya.${period.name}.${stage.name}")
    private fun cancel(period: SandhyaPeriod, stage: SandhyaReminderStage) {
        PendingIntent.getBroadcast(context, period.requestCode, alarmIntent(period, stage),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
    }
}

fun SandhyaSettings.stageEnabled(stage: SandhyaReminderStage) =
    if (stage == SandhyaReminderStage.PREPARATION) preparationEnabled else eventEnabled
