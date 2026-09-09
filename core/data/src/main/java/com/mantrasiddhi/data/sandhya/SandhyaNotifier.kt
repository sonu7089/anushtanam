/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.sandhya

import com.mantrasiddhi.domain.model.SandhyaReminderStage
import android.os.Build
import android.net.Uri
import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.model.SandhyaPeriod
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts the Sandhya reminder notifications, and owns the notification
 * channel. The channel is created up front (from [com.mantrasiddhi.MantraSiddhiApplication])
 * so the user can configure it in system settings before the first reminder.
 */
@Singleton
class SandhyaNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /** Idempotent; safe to call on every app start. */
    fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(
            SandhyaContract.CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName(context.getString(R.string.sandhya_channel_name))
            .setDescription(context.getString(R.string.sandhya_channel_desc))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Posts the reminder for [period]. No-ops (without throwing) when POST_NOTIFICATIONS
     * is not granted, so scheduling never depends on the permission.
     */
    fun showReminder(period: SandhyaPeriod, eventMillis: Long, zone: ZoneId, dateKey: String, userId: Long, stage: SandhyaReminderStage): Boolean {
        if (!notificationsAllowed()) return false

        val periodName = context.getString(periodNameRes(period))
        val timeText = timeFormatter.format(Instant.ofEpochMilli(eventMillis).atZone(zone))
        val preparation = stage == SandhyaReminderStage.PREPARATION
        val late = System.currentTimeMillis() > eventMillis + 120_000
        val title = context.getString(if (preparation) R.string.reminder_prepare_title else R.string.reminder_event_title)
        val text = context.getString(when {
            preparation -> R.string.reminder_prepare_body
            late -> R.string.reminder_event_late_body
            else -> R.string.reminder_event_body
        }, periodName, timeText)

        val notification = NotificationCompat.Builder(context, SandhyaContract.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sandhya_sun)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(period, dateKey, userId, stage))
            .addAction(
                R.drawable.ic_sandhya_sun,
                context.getString(if (preparation) R.string.reminder_read_guide else R.string.reminder_record_count),
                openAppIntent(period, dateKey, userId, stage)
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(period.requestCode, notification)
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    /** Dismisses a posted reminder (used once the user marks it done). */
    fun cancel(period: SandhyaPeriod) {
        NotificationManagerCompat.from(context).cancel(period.requestCode)
    }

    fun notificationsAllowed(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(SandhyaContract.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun openAppIntent(period: SandhyaPeriod, dateKey: String, userId: Long, stage: SandhyaReminderStage): PendingIntent {
        val launch = Intent().setClassName(context.packageName, "com.mantrasiddhi.presentation.MainActivity").apply {
            action = SandhyaContract.ACTION_OPEN
            data = Uri.parse("anushtanam://sandhya/$userId/$dateKey/${period.name}/${stage.name}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(SandhyaContract.EXTRA_PERIOD, period.name)
            putExtra(SandhyaContract.EXTRA_DATE_KEY, dateKey)
            putExtra(SandhyaContract.EXTRA_USER_ID, userId)
            putExtra(SandhyaContract.EXTRA_STAGE, stage.name)
        }
        return PendingIntent.getActivity(context, period.requestCode, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        fun periodNameRes(period: SandhyaPeriod): Int = when (period) {
            SandhyaPeriod.PRATAH -> R.string.sandhya_period_pratah
            SandhyaPeriod.MADHYAHNA -> R.string.sandhya_period_madhyahna
            SandhyaPeriod.SAYAM -> R.string.sandhya_period_sayam
        }
    }
}
