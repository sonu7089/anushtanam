/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.sandhya

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.data.preferences.SandhyaSettings
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.solar.SolarCalculator
import com.mantrasiddhi.domain.util.SandhyaDateKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the three daily Sandhya reminders with [AlarmManager].
 *
 * Design notes (Play-policy compliant, dependency-free):
 *  - Uses the **inexact** [AlarmManager.setAndAllowWhileIdle]. A devotional nudge is
 *    a soft reminder, not an alarm-clock event, so we declare neither USE_EXACT_ALARM
 *    nor SCHEDULE_EXACT_ALARM (both would draw Play exact-alarm review a sunrise
 *    reminder can't justify). Doze may delay delivery by minutes; that's acceptable
 *    and surfaced in the UI.
 *  - Each period has a distinct, stable request code, so the three alarms are
 *    independent and individually cancellable.
 *  - Single-shot + self-rearming: each fired alarm re-arms only itself for the next
 *    day (done in [SandhyaAlarmReceiver]); boot / time-change re-arms all three.
 */
@Singleton
class SandhyaScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: SandhyaPreferences
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Re-arms every period if enabled, or cancels everything if disabled. */
    suspend fun scheduleAll() {
        val settings = preferences.current()
        if (!settings.enabled || settings.userId <= 0L) {
            cancelAll()
            return
        }
        SandhyaPeriod.entries.forEach { schedule(it, settings) }
    }

    /** Re-arms a single period (used by the alarm receiver after it fires). */
    suspend fun scheduleNext(period: SandhyaPeriod) {
        val settings = preferences.current()
        if (!settings.enabled || settings.userId <= 0L) {
            cancel(period)
            return
        }
        schedule(period, settings)
    }

    fun cancelAll() = SandhyaPeriod.entries.forEach { cancel(it) }

    private fun schedule(period: SandhyaPeriod, settings: SandhyaSettings) {
        val eventMillis = nextEventMillis(period, settings) ?: run {
            // Polar day/night (no Indian city hits this) — nothing to arm today.
            cancel(period)
            return
        }
        // Compute the target local day once, here, and carry it on the intent so the
        // receiver buckets the occurrence under the same day the UI assumes (avoids a
        // timezone/DST shift between scheduling and firing splitting an anushtanam day).
        val dateKey = SandhyaDateKey.of(eventMillis, settings.zone())
        val triggerAt = eventMillis - SandhyaContract.LEAD_MILLIS
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            alarmPendingIntent(period, eventMillis, dateKey)
        )
    }

    private fun cancel(period: SandhyaPeriod) {
        // FLAG_NO_CREATE: resolve an existing PendingIntent to cancel, or no-op if none.
        cancelPendingIntent(period)?.let { alarmManager.cancel(it) }
    }

    /**
     * Epoch ms of the next occurrence of [period]'s solar event whose reminder time
     * is still in the future. Looks a few days ahead so a polar gap or an event that
     * already passed today rolls forward. Null only if persistently undefined (polar).
     */
    private fun nextEventMillis(period: SandhyaPeriod, settings: SandhyaSettings): Long? {
        val zone = settings.zone()
        val region = settings.region
        val now = System.currentTimeMillis()
        var date = LocalDate.now(zone)
        repeat(LOOKAHEAD_DAYS) {
            val epochs = SolarCalculator.computeEpochMillis(date, region.latitude, region.longitude, zone)
            val eventMillis = when (period) {
                SandhyaPeriod.PRATAH -> epochs.sunriseUtcMillis
                SandhyaPeriod.MADHYAHNA -> epochs.solarNoonUtcMillis
                SandhyaPeriod.SAYAM -> epochs.sunsetUtcMillis
            }
            if (eventMillis != null && eventMillis - SandhyaContract.LEAD_MILLIS > now + FUTURE_BUFFER_MS) {
                return eventMillis
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun alarmPendingIntent(period: SandhyaPeriod, eventMillis: Long, dateKey: String): PendingIntent {
        val intent = Intent(context, SandhyaAlarmReceiver::class.java).apply {
            putExtra(SandhyaContract.EXTRA_PERIOD, period.name)
            putExtra(SandhyaContract.EXTRA_EVENT_MILLIS, eventMillis)
            putExtra(SandhyaContract.EXTRA_DATE_KEY, dateKey)
        }
        return PendingIntent.getBroadcast(
            context,
            period.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Equivalent PendingIntent for cancellation; null if none is currently armed. */
    private fun cancelPendingIntent(period: SandhyaPeriod): PendingIntent? {
        val intent = Intent(context, SandhyaAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            period.requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val LOOKAHEAD_DAYS = 4
        const val FUTURE_BUFFER_MS = 1_000L
    }
}
