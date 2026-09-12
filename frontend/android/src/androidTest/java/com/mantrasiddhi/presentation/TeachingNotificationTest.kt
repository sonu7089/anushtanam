package com.mantrasiddhi.presentation

import android.Manifest
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mantrasiddhi.data.messages.TeachingNotifications
import com.mantrasiddhi.domain.model.TeachingMessage
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TeachingNotificationTest {
    @Test fun unchangedPermissionRefreshAndEmptyMaintenanceDoNotCommitPreferences() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val name = "maintenance-${UUID.randomUUID()}"
        val storage = base.getSharedPreferences(name, Context.MODE_PRIVATE)
        val commits = java.util.concurrent.atomic.AtomicInteger()
        val tracked = object : SharedPreferences by storage {
            override fun edit(): SharedPreferences.Editor {
                val editor = storage.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit(): Boolean { commits.incrementAndGet(); return editor.commit() }
                }
            }
        }
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = tracked
        }
        val firebase = FirebaseApp.initializeApp(base, FirebaseOptions.Builder().setProjectId("demo-message-test")
            .setApplicationId("1:123456789:android:abcdef").setApiKey("test-key").build(), name)
        try {
            val notifications = TeachingNotifications(context, FirebaseFirestore.getInstance(firebase), FirebaseAuth.getInstance(firebase))
            notifications.permissionResult(false)
            instrumentation.runOnMainSync { repeat(20) { notifications.refreshPermission() } }
            repeat(20) { assertTrue(notifications.pending().isEmpty()) }
            assertEquals(0, commits.get())
            storage.edit().putLong("seen_old", 1).commit()
            notifications.pending()
            assertEquals(1, commits.get())
            notifications.pending()
            assertEquals(1, commits.get())
        } finally {
            base.deleteSharedPreferences(name)
            FirebaseFirestore.getInstance(firebase).terminate()
            firebase.delete()
        }
    }

    @Test fun queuedMessageWaitsForJapaAndDuplicateReceiptDoesNotPostAgain() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val prefsName = "teaching-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(prefsName, mode)
        }
        // An isolated signed-out Firebase instance ensures this test never registers with production.
        val firebase = FirebaseApp.initializeApp(base, FirebaseOptions.Builder().setProjectId("demo-message-test")
            .setApplicationId("1:123456789:android:abcdef").setApiKey("test-key").build(), prefsName)
        val manager = base.getSystemService(NotificationManager::class.java)
        val notifications = TeachingNotifications(context, FirebaseFirestore.getInstance(firebase), FirebaseAuth.getInstance(firebase))
        val id = "test-${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val message = TeachingMessage("test-message", 1, "Begin with care", "A little time for you.", "Body", "", now - 1, now + 60000)
        val data = mapOf("schemaVersion" to "1", "campaignId" to id, "contentId" to message.id,
            "expiresAt" to message.expiresAt.toString())
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        try {
            notifications.ensureChannel()
            notifications.permissionResult(true)
            assertTrue(notifications.isEnabledNow())
            notifications.setPracticing(true)
            notifications.receive(data)
            assertTrue(notifications.pending().containsKey(id))
            val reopened = TeachingNotifications(context, FirebaseFirestore.getInstance(firebase), FirebaseAuth.getInstance(firebase))
            assertTrue(reopened.pending().containsKey(id))
            notifications.display(id, message.expiresAt, message)
            assertFalse(manager.activeNotifications.any { it.tag == "teaching_$id" })
            notifications.setPracticing(false)
            notifications.display(id, message.expiresAt, message)
            // NotificationManager enqueues delivery in another process; wait for that acknowledgement.
            val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
            while (manager.activeNotifications.none { it.tag == "teaching_$id" } &&
                android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
            val posted = manager.activeNotifications.first { it.tag == "teaching_$id" }
            assertTrue(posted.notification.contentIntent.isImmutable)
            assertEquals(TeachingNotifications.CHANNEL, posted.notification.channelId)
            assertFalse(notifications.pending().containsKey(id))
            notifications.receive(data)
            assertFalse(notifications.pending().containsKey(id))
            notifications.setPracticing(true)
            notifications.receive(data + ("campaignId" to "next"))
            assertTrue(notifications.pending().containsKey("next"))
            notifications.setEnabled(false)
            assertTrue(notifications.pending().isEmpty())
            notifications.setPracticing(false)
            notifications.display("off", message.expiresAt, message)
            assertFalse(manager.activeNotifications.any { it.tag == "teaching_off" })
            notifications.permissionResult(false)
            assertFalse(notifications.enabled.value)
            notifications.permissionResult(true)
            notifications.receive(data + mapOf("campaignId" to "expired", "expiresAt" to (now - 1).toString()))
            notifications.receive(data + mapOf("campaignId" to "bad-route", "contentId" to "../private"))
            assertTrue(notifications.pending().isEmpty())
        } finally {
            manager.cancel("teaching_$id", 0)
            base.getSystemService(JobScheduler::class.java).cancel(3010)
            base.deleteSharedPreferences(prefsName)
            FirebaseFirestore.getInstance(firebase).terminate()
            firebase.delete()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
