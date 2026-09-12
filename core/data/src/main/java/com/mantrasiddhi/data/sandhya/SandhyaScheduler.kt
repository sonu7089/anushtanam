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

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SandhyaScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: SandhyaPreferences,
    private val logs: SandhyaLogRepository
) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val lock = Mutex()

    private val notificationPermission = MutableStateFlow<Boolean?>(null)
    fun updateNotificationPermission(allowed: Boolean) { notificationPermission.value = allowed }

    private val retryRevision = MutableStateFlow(0L)
    @Volatile private var retryUser: Long? = null
    private val startupUser = MutableStateFlow<Long?>(null)
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Process-owned: leaving the entry screen cannot cancel reminder re-arming.
        // Account replacements cancel stale work; the lock guards the current owner check.
        startupScope.launch {
            startupUser.collectLatest { userId ->
                if (userId == null) return@collectLatest
                combine(preferences.settings.distinctUntilChanged(), notificationPermission, retryRevision) { settings, allowed, _ -> settings to allowed }
                    .collectLatest settingsChanged@ { (settings, _) ->
                    if (settings.userId != userId) return@settingsChanged
                    val source = if (!settings.enabled || (!settings.preparationEnabled && !settings.eventEnabled))
                        flowOf(emptyList()) else logs.observeLogs(userId)
                    source.distinctUntilChangedBy { entries ->
                        entries.filter { it.status == SandhyaStatus.OBSERVED }.map { it.period to it.dateKey }.toSet()
                    }.collect { entries ->
                        try {
                            com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("reminder.owner.update")
                            scheduleFor(userId, observedLogs = entries)
                            retryUser = null
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) {
                            retryUser = userId
                            Log.w("SandhyaReminder", "Reminder update failed; retry on next change or startup", failure)
                        }
                    }
                }
            }
        }
    }

    fun requestStartupRearm(userId: Long) {
        if (startupUser.value == userId && retryUser == userId) retryRevision.value++
        else startupUser.value = userId
    }

    suspend fun scheduleAll(force: Boolean = false) = scheduleFor(null, force)

    private suspend fun scheduleFor(expectedUserId: Long?, force: Boolean = false, observedLogs: List<SandhyaLog>? = null) = lock.withLock {
        val settings = preferences.current()
        if (expectedUserId != null && settings.userId != expectedUserId) return@withLock
        val planned = upcoming(settings, observedLogs = observedLogs)
        if (force) armed.clear()
        SandhyaPeriod.entries.forEach { period ->
            // Remove pre-MF-06 alarms, whose identity had no stage.
            PendingIntent.getBroadcast(context, period.requestCode, Intent(context, SandhyaAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
            SandhyaReminderStage.entries.forEach { stage -> arm(period, stage, settings, planned[period to stage]) }
        }
    }

    suspend fun scheduleNext(period: SandhyaPeriod, stage: SandhyaReminderStage, afterEvent: Long) = lock.withLock {
        val settings = preferences.current()
        val observed = if (settings.enabled && settings.userId > 0) logs.getLogs(settings.userId) else emptyList()
        armed.remove(period to stage)
        arm(period, stage, settings, next(period, stage, settings, System.currentTimeMillis(), observed, mutableMapOf(), afterEvent))
    }

    fun cancelAll() {
        startupUser.value = null
        armed.clear()
        SandhyaPeriod.entries.forEach { period ->
            PendingIntent.getBroadcast(context, period.requestCode, Intent(context, SandhyaAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
            SandhyaReminderStage.entries.forEach { cancel(period, it) }
        }
    }

    suspend fun upcoming(settings: SandhyaSettings, now: Long = System.currentTimeMillis(),
        observedLogs: List<SandhyaLog>? = null): Map<Pair<SandhyaPeriod, SandhyaReminderStage>, SandhyaReminderTime> {
        if (!settings.enabled || settings.userId <= 0 || (!settings.preparationEnabled && !settings.eventEnabled)) return emptyMap()
        val observed = observedLogs ?: logs.getLogs(settings.userId)
        val solar = mutableMapOf<LocalDate, com.mantrasiddhi.domain.solar.SolarEpochs>()
        com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("reminder.upcoming")
        return buildMap {
            for (period in SandhyaPeriod.entries) for (stage in SandhyaReminderStage.entries) {
                next(period, stage, settings, now, observed, solar)?.let { put(period to stage, it) }
            }
        }
    }

    private suspend fun next(period: SandhyaPeriod, stage: SandhyaReminderStage, settings: SandhyaSettings,
        now: Long, observedLogs: List<SandhyaLog>, solar: MutableMap<LocalDate, com.mantrasiddhi.domain.solar.SolarEpochs>,
        afterEvent: Long = 0): SandhyaReminderTime? {
        if (!settings.enabled || settings.userId <= 0 || !settings.stageEnabled(stage)) return null
        val delivered = maxOf(afterEvent, preferences.delivered(settings.userId, period, stage))
        val deliveredDate = preferences.deliveredDate(settings.userId, period, stage)
        val observed = observedLogs.filter { it.period == period && it.status == SandhyaStatus.OBSERVED }.map { it.dateKey }.toSet()
        var date = java.time.Instant.ofEpochMilli(now).atZone(settings.zone()).toLocalDate()
        repeat(8) {
            val epochs = solar.getOrPut(date) { solarFor(date, settings) }
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

    private data class SolarKey(val date: LocalDate, val latitude: Double, val longitude: Double, val zone: String)
    private val solarCache = linkedMapOf<SolarKey, com.mantrasiddhi.domain.solar.SolarEpochs>()
    private fun solarFor(date: LocalDate, settings: SandhyaSettings) = synchronized(solarCache) {
        val key = SolarKey(date, settings.region.latitude, settings.region.longitude, settings.zone().id)
        solarCache.getOrPut(key) {
            if (solarCache.size >= 32) solarCache.remove(solarCache.keys.first())
            com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("reminder.solar.compute")
            SolarCalculator.computeEpochMillis(date, key.latitude, key.longitude, settings.zone())
        }
    }

    private data class Armed(val settings: SandhyaSettings, val zone: String, val time: SandhyaReminderTime)
    private val armed = java.util.concurrent.ConcurrentHashMap<Pair<SandhyaPeriod, SandhyaReminderStage>, Armed>()

    private suspend fun arm(period: SandhyaPeriod, stage: SandhyaReminderStage, settings: SandhyaSettings, next: SandhyaReminderTime?) {
        currentCoroutineContext().ensureActive()
        // A sign-out/account switch may have happened during the log query.
        if (preferences.current() != settings) return
        if (next == null) { cancel(period, stage); return }
        val key = period to stage
        val desired = Armed(settings, settings.zone().id, next)
        if (armed[key] == desired) return
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
        armed[key] = desired
        com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("reminder.alarm.write")
        Log.d("SandhyaReminder", "scheduled $period $stage at ${next.triggerMillis}")
    }
    private fun alarmIntent(period: SandhyaPeriod, stage: SandhyaReminderStage) =
        Intent(context, SandhyaAlarmReceiver::class.java).setAction("sandhya.${period.name}.${stage.name}")
    private fun cancel(period: SandhyaPeriod, stage: SandhyaReminderStage) {
        armed.remove(period to stage)
        PendingIntent.getBroadcast(context, period.requestCode, alarmIntent(period, stage),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
    }
}

fun SandhyaSettings.stageEnabled(stage: SandhyaReminderStage) =
    if (stage == SandhyaReminderStage.PREPARATION) preparationEnabled else eventEnabled
