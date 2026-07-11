/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.sandhya

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
import com.mantrasiddhi.R
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
    fun showReminder(period: SandhyaPeriod, eventMillis: Long, zone: ZoneId, dateKey: String) {
        if (!hasPermission()) return

        val periodName = context.getString(periodNameRes(period))
        val timeText = timeFormatter.format(Instant.ofEpochMilli(eventMillis).atZone(zone))
        val title = context.getString(R.string.sandhya_notif_title, periodName)
        val text = context.getString(R.string.sandhya_notif_text, periodName, timeText)

        val notification = NotificationCompat.Builder(context, SandhyaContract.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sandhya_sun)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(
                R.drawable.ic_sandhya_sun,
                context.getString(R.string.sandhya_mark_done),
                markObservedIntent(period, dateKey, eventMillis)
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(period.requestCode, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; ignore.
        }
    }

    /** Dismisses a posted reminder (used once the user marks it done). */
    fun cancel(period: SandhyaPeriod) {
        NotificationManagerCompat.from(context).cancel(period.requestCode)
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        return PendingIntent.getActivity(
            context,
            0,
            launch ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun markObservedIntent(period: SandhyaPeriod, dateKey: String, eventMillis: Long): PendingIntent {
        val intent = Intent(context, SandhyaActionReceiver::class.java).apply {
            action = SandhyaContract.ACTION_MARK_OBSERVED
            putExtra(SandhyaContract.EXTRA_PERIOD, period.name)
            putExtra(SandhyaContract.EXTRA_DATE_KEY, dateKey)
            putExtra(SandhyaContract.EXTRA_EVENT_MILLIS, eventMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            period.requestCode + SandhyaContract.ACTION_REQUEST_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        fun periodNameRes(period: SandhyaPeriod): Int = when (period) {
            SandhyaPeriod.PRATAH -> R.string.sandhya_period_pratah
            SandhyaPeriod.MADHYAHNA -> R.string.sandhya_period_madhyahna
            SandhyaPeriod.SAYAM -> R.string.sandhya_period_sayam
        }
    }
}
