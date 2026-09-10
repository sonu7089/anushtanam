package com.mantrasiddhi.data.messages

import android.Manifest
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.mantrasiddhi.domain.model.TeachingDeliveryPolicy
import com.mantrasiddhi.resources.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeachingNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val prefs = context.getSharedPreferences("teaching_notifications", Context.MODE_PRIVATE)
    private val manager get() = NotificationManagerCompat.from(context)
    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()
    @Volatile var practicing: Boolean = false
        private set
    private val installationId: String by lazy {
        val file = File(context.noBackupFilesDir, "teaching-installation-id")
        if (file.exists()) file.readText() else UUID.randomUUID().toString().also { file.writeText(it) }
    }
    private var started = false

    fun start() {
        if (started) return
        started = true
        ensureChannel()
        refreshPermission()
        auth.addAuthStateListener { register() }
        schedulePending()
    }

    fun ensureChannel() {
        manager.createNotificationChannel(NotificationChannelCompat.Builder(CHANNEL,
            NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(context.getString(R.string.messages_title))
            .setDescription(context.getString(R.string.messages_notification_description)).build())
    }

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED && manager.areNotificationsEnabled()

    fun needsFirstRequest(): Boolean = !prefs.getBoolean("permissionAsked", false)
    fun markPermissionAsked() { prefs.edit().putBoolean("permissionAsked", true).commit() }
    fun permissionResult(granted: Boolean) {
        prefs.edit().putBoolean("permissionAsked", true).putBoolean("wanted", granted).commit()
        refreshPermission()
    }

    fun refreshPermission() {
        ensureChannel()
        // Keep a deliberate app opt-out when Android permission changes elsewhere.
        _enabled.value = prefs.getBoolean("wanted", true) && hasPermission() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManagerCompat.IMPORTANCE_NONE
        if (!_enabled.value) clearPending()
        register()
        schedulePending()
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("wanted", value).commit()
        refreshPermission()
    }

    fun register(token: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        if (token == null) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { register(it) }
            return
        }
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        // One document per installation and account. Tokens are never placed in the public feed.
        firestore.collection("messageInstallations").document("${uid}_$installationId").set(mapOf(
            "uid" to uid, "token" to token, "enabled" to _enabled.value,
            "schemaVersion" to 1, "locale" to "en", "appVersion" to version,
            "updatedAt" to FieldValue.serverTimestamp()
        ))
    }

    @Synchronized fun receive(data: Map<String, String>) {
        if (data["schemaVersion"] != "1") return
        val id = data["campaignId"] ?: return
        val contentId = data["contentId"] ?: return
        val expires = data["expiresAt"]?.toLongOrNull() ?: return
        if (!TeachingDeliveryPolicy.validId(id) || !TeachingDeliveryPolicy.validId(contentId) ||
            expires <= System.currentTimeMillis() || !isEnabledNow() || prefs.contains("seen_$id")) return
        prefs.edit().putString("pending_$id", JSONObject().put("contentId", contentId)
            .put("expiresAt", expires).toString()).commit()
        schedulePending()
    }

    fun isEnabledNow() = prefs.getBoolean("wanted", true) && hasPermission() &&
        manager.getNotificationChannel(CHANNEL)?.importance != NotificationManagerCompat.IMPORTANCE_NONE

    @Synchronized fun pending(): Map<String, String> {
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        prefs.all.filterKeys { it.startsWith("seen_") }.forEach { (key, value) ->
            if ((value as? Long ?: 0) <= now) editor.remove(key)
        }
        val result = mutableMapOf<String, String>()
        prefs.all.filterKeys { it.startsWith("pending_") }.forEach { (key, value) ->
            val payload = runCatching { JSONObject(value as String) }.getOrNull()
            if (payload == null || payload.optLong("expiresAt") <= now) editor.remove(key)
            else result[key.removePrefix("pending_")] = value as String
        }
        editor.commit()
        return result
    }

    @Synchronized fun discard(id: String) { prefs.edit().remove("pending_$id").commit() }
    @Synchronized private fun clearPending() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("pending_") }.forEach(editor::remove)
        editor.commit()
    }

    @Synchronized fun display(id: String, expiresAt: Long, message: com.mantrasiddhi.domain.model.TeachingMessage) {
        if (!TeachingDeliveryPolicy.canDisplay(isEnabledNow(), practicing, expiresAt, System.currentTimeMillis()) ||
            !message.isVisible(System.currentTimeMillis()) || prefs.contains("seen_$id")) return
        val intent = Intent().setClassName(context.packageName, "com.mantrasiddhi.presentation.MainActivity")
            .setData(Uri.parse("anushtanam://messages/${message.id}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_sandhya_sun)
            .setContentTitle(message.title).setContentText(message.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.summary)).setContentIntent(tap)
            .setOnlyAlertOnce(true).setAutoCancel(true).setTimeoutAfter(expiresAt - System.currentTimeMillis()).build()
        // Commit before posting, so a process crash cannot replay an alert. Posting itself is best effort.
        prefs.edit().putLong("seen_$id", expiresAt).remove("pending_$id").commit()
        try { manager.notify("teaching_$id", 0, notification) } catch (_: SecurityException) { }
    }

    fun setPracticing(value: Boolean) {
        practicing = value
        if (!value) schedulePending()
    }

    fun schedulePending(delayMillis: Long = 0) {
        if (pending().isEmpty()) return
        context.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(JOB_ID,
            ComponentName(context, TeachingDeliveryJob::class.java))
            .setMinimumLatency(delayMillis).setPersisted(true).build())
    }

    companion object {
        const val CHANNEL = "teachings_messages"
        private const val JOB_ID = 3010
    }
}
