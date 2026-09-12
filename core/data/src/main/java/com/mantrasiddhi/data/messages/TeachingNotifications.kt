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
import com.mantrasiddhi.domain.model.NotificationNudge
import com.mantrasiddhi.resources.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.tasks.await
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
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registrationRequests = Channel<Unit>(Channel.CONFLATED)
    private val registration = RegistrationCoalescer<RegistrationInputs>()
    @Volatile private var cachedToken: String? = null
    private var permissionKnown = false
    private data class RegistrationInputs(val uid: String, val token: String, val enabled: Boolean, val version: Long)

    init {
        maintenanceScope.launch {
            for (request in registrationRequests) {
                try {
                    val uid = auth.currentUser?.uid ?: continue
                    val token = cachedToken ?: FirebaseMessaging.getInstance().token.await().also {
                        if (cachedToken == null) cachedToken = it
                    }
                    if (auth.currentUser?.uid != uid) continue
                    val input = RegistrationInputs(uid, cachedToken ?: token, _enabled.value,
                        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
                    registration.update(input) {
                        com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("notification.registration.write")
                        firestore.collection("messageInstallations").document("${uid}_$installationId").set(mapOf(
                            "uid" to uid, "token" to input.token, "enabled" to input.enabled,
                            "schemaVersion" to 1, "locale" to "en", "appVersion" to input.version,
                            "notificationOnly" to true, "updatedAt" to FieldValue.serverTimestamp()
                        )).await()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Retry the current inputs on the next refresh/token/auth event. */ }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        ensureChannel()
        refreshPermission()
        auth.addAuthStateListener { register() }
    }

    private var channelCreated = false
    @Synchronized fun ensureChannel() {
        if (channelCreated) return
        manager.createNotificationChannel(NotificationChannelCompat.Builder(CHANNEL,
            NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(context.getString(R.string.messages_title))
            .setDescription(context.getString(R.string.messages_notification_description)).build())
        channelCreated = true
    }

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED && manager.areNotificationsEnabled()

    fun needsFirstRequest(): Boolean = !prefs.getBoolean("permissionAsked", false)
    fun markPermissionAsked() { prefs.edit().putBoolean("permissionAsked", true).apply() }
    fun permissionResult(granted: Boolean) {
        prefs.edit().putBoolean("permissionAsked", true).putBoolean("wanted", granted).apply()
        refreshPermission()
    }

    fun refreshPermission() {
        ensureChannel()
        // Keep a deliberate app opt-out when Android permission changes elsewhere.
        val wasEnabled = _enabled.value
        _enabled.value = prefs.getBoolean("wanted", true) && hasPermission() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManagerCompat.IMPORTANCE_NONE
        if (!permissionKnown || wasEnabled != _enabled.value) {
            permissionKnown = true
            if (!_enabled.value) clearPending() else schedulePending()
        }
        register()
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("wanted", value).apply()
        refreshPermission()
    }

    fun register(token: String? = null) {
        if (token != null) cachedToken = token
        registrationRequests.trySend(Unit)
    }

    @Synchronized fun receive(data: Map<String, String>) {
        if (data["kind"] == "nudge") {
            val nudge = NotificationNudge.fromPayload(data, System.currentTimeMillis()) ?: return
            if (!isEnabledNow() || prefs.contains("seen_${nudge.campaignId}")) return
            prefs.edit().putString("pending_${nudge.campaignId}", JSONObject(nudge.toPayload()).toString()).commit()
            schedulePending()
            return
        }
        if (data["kind"] != null && data["kind"] != "teaching") return
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
        var changed = false
        val snapshot = prefs.all
        snapshot.filterKeys { it.startsWith("seen_") }.forEach { (key, value) ->
            if ((value as? Long ?: 0) <= now) { editor.remove(key); changed = true }
        }
        val result = mutableMapOf<String, String>()
        snapshot.filterKeys { it.startsWith("pending_") }.forEach { (key, value) ->
            val payload = runCatching { JSONObject(value as String) }.getOrNull()
            if (payload == null || payload.optLong("expiresAt") <= now) { editor.remove(key); changed = true }
            else result[key.removePrefix("pending_")] = value as String
        }
        if (changed) { editor.commit(); com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("notification.maintenance.commit") }
        return result
    }

    @Synchronized fun discard(id: String) { prefs.edit().remove("pending_$id").commit() }
    @Synchronized private fun clearPending() {
        val keys = prefs.all.keys.filter { it.startsWith("pending_") }
        if (keys.isEmpty()) return
        val editor = prefs.edit()
        keys.forEach(editor::remove)
        editor.apply()
    }

    @Synchronized fun display(id: String, expiresAt: Long, message: com.mantrasiddhi.domain.model.TeachingMessage) {
        if (!message.isVisible(System.currentTimeMillis())) return
        post(id, expiresAt, message.title, message.summary, "anushtanam://messages/${message.id}")
    }

    @Synchronized fun displayNudge(nudge: NotificationNudge) {
        if (NotificationNudge.fromPayload(nudge.toPayload(), System.currentTimeMillis()) == null) return
        post(nudge.campaignId, nudge.expiresAt, nudge.title, nudge.body, "anushtanam://home/${nudge.campaignId}")
    }

    private fun post(id: String, expiresAt: Long, title: String, body: String, destination: String) {
        if (!TeachingDeliveryPolicy.canDisplay(isEnabledNow(), practicing, expiresAt, System.currentTimeMillis()) ||
            prefs.contains("seen_$id")) return
        val intent = Intent().setClassName(context.packageName, "com.mantrasiddhi.presentation.MainActivity")
            .setData(Uri.parse(destination))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_sandhya_sun)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(tap)
            .setOnlyAlertOnce(true).setAutoCancel(true).setTimeoutAfter(expiresAt - System.currentTimeMillis()).build()
        // Commit before posting, so a process crash cannot replay an alert. Posting itself is best effort.
        prefs.edit().putLong("seen_$id", expiresAt).remove("pending_$id").commit()
        try { manager.notify("teaching_$id", 0, notification) } catch (_: SecurityException) { }
    }

    fun setPracticing(value: Boolean) {
        val changed = practicing != value
        practicing = value
        if (changed && !value) schedulePending()
    }

    fun schedulePending(delayMillis: Long = 0) {
        maintenanceScope.launch {
            if (pending().isEmpty()) return@launch
            context.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(JOB_ID,
                ComponentName(context, TeachingDeliveryJob::class.java))
                .setMinimumLatency(delayMillis).setPersisted(true).build())
        }
    }

    companion object {
        const val CHANNEL = "teachings_messages"
        private const val JOB_ID = 3010
    }
}
