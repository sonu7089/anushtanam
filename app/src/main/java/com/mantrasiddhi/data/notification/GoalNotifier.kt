/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.RewardType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a local celebratory notification when a parent sets a recitation goal for
 * a child (same-device MVP). Owns its own notification channel, created up front so
 * the user can tune it in system settings before the first goal is set.
 *
 * Mirrors the structure/style of [com.mantrasiddhi.data.sandhya.SandhyaNotifier].
 */
@Singleton
class GoalNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationId = AtomicInteger(NOTIFICATION_ID_BASE)

    /** Idempotent; safe to call on every app start. */
    fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName(context.getString(R.string.goal_channel_name))
            .setDescription(context.getString(R.string.goal_channel_desc))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Posts the "new goal from your parent" notification. No-ops (without throwing)
     * when POST_NOTIFICATIONS is not granted, so goal-setting never depends on it.
     *
     * @param count The recitation count the child must reach.
     * @param rewardType The kind of reward promised (for the emoji glyph).
     * @param rewardDescription Free-text reward; when blank a generic reward label is used.
     */
    fun showGoalSet(count: Int, rewardType: RewardType, rewardDescription: String) {
        if (!hasPermission()) return

        val reward = if (rewardDescription.isNotBlank()) {
            "${rewardType.emoji} $rewardDescription"
        } else {
            context.getString(R.string.goal_notif_reward_generic)
        }
        val title = context.getString(R.string.goal_notif_title)
        val text = context.getString(R.string.goal_notif_text, count, reward)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sandhya_sun)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId.getAndIncrement(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; ignore.
        }
    }

    /**
     * Posts a generic goal notification with an explicit [title] and [body]. Used by the
     * cloud push path (FCM data message) and the same-device Firestore listener that
     * surfaces newly-created goals. No-ops (without throwing) when POST_NOTIFICATIONS is
     * not granted.
     */
    fun showGeneric(title: String, body: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sandhya_sun)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId.getAndIncrement(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; ignore.
        }
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

    private companion object {
        const val CHANNEL_ID = "goal_reminders"
        const val NOTIFICATION_ID_BASE = 2000
    }
}
