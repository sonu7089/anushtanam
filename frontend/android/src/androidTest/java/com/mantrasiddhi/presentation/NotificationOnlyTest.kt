package com.mantrasiddhi.presentation

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mantrasiddhi.data.messages.TeachingNotifications
import com.mantrasiddhi.domain.model.NotificationNudge
import com.mantrasiddhi.presentation.navigation.HomeNotificationTarget
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NotificationOnlyTest {
    @Test fun standaloneNotificationPersistsWaitsForJapaAndPostsOnceWithItsOwnText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val prefsName = "nudge-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences(prefsName, mode)
        }
        val firebase = FirebaseApp.initializeApp(base, FirebaseOptions.Builder().setProjectId("demo-nudge-test")
            .setApplicationId("1:123456789:android:abcdef").setApiKey("test-key").build(), prefsName)
        val manager = base.getSystemService(NotificationManager::class.java)
        fun notifications() = TeachingNotifications(context, FirebaseFirestore.getInstance(firebase), FirebaseAuth.getInstance(firebase))
        val notifications = notifications()
        val note = NotificationNudge("test-${UUID.randomUUID()}", "A little something for the festival",
            "Your festival offer is ready. Tap to take a look.", System.currentTimeMillis() + 60000)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        try {
            notifications.ensureChannel()
            notifications.permissionResult(true)
            notifications.setPracticing(true)
            notifications.receive(note.toPayload())
            assertTrue(notifications().pending().containsKey(note.campaignId))
            notifications.displayNudge(note)
            assertFalse(manager.activeNotifications.any { it.tag == "teaching_${note.campaignId}" })
            notifications.setPracticing(false)
            notifications.displayNudge(note)
            val posted = manager.activeNotifications.first { it.tag == "teaching_${note.campaignId}" }
            assertEquals(note.title, posted.notification.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(note.body, posted.notification.extras.getString(Notification.EXTRA_TEXT))
            assertEquals(TeachingNotifications.CHANNEL, posted.notification.channelId)
            assertTrue(posted.notification.contentIntent.isImmutable)
            assertTrue(notifications.pending().isEmpty())
            notifications.receive(note.toPayload())
            assertTrue(notifications.pending().isEmpty())
            notifications.setPracticing(true)
            notifications.receive(note.copy(campaignId = "waiting").toPayload())
            assertTrue(notifications.pending().containsKey("waiting"))
            notifications.setEnabled(false)
            assertTrue(notifications.pending().isEmpty())
            notifications.setPracticing(false)
            notifications.displayNudge(note.copy(campaignId = "off"))
            assertFalse(manager.activeNotifications.any { it.tag == "teaching_off" })
            notifications.permissionResult(false)
            notifications.receive(note.copy(campaignId = "permission-off").toPayload())
            assertTrue(notifications.pending().isEmpty())
            notifications.permissionResult(true)
            notifications.receive(note.copy(campaignId = "expired", expiresAt = 1).toPayload())
            notifications.receive(note.copy(campaignId = "bad").toPayload() + ("destination" to "https://example.com"))
            assertTrue(notifications.pending().isEmpty())
        } finally {
            manager.cancel("teaching_${note.campaignId}", 0)
            base.getSystemService(JobScheduler::class.java).cancel(3010)
            base.deleteSharedPreferences(prefsName)
            FirebaseFirestore.getInstance(firebase).terminate()
            firebase.delete()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test fun homeTapAcceptsOnlyTheSupportedDestination() {
        fun target(uri: String) = HomeNotificationTarget.from(Intent().setData(Uri.parse(uri)))
        assertEquals("festival-1", target("anushtanam://home/festival-1"))
        listOf("https://home/festival-1", "anushtanam://messages/festival-1", "anushtanam://home/",
            "anushtanam://home/a/b", "anushtanam://home/..", "anushtanam://home/a?url=elsewhere",
            "anushtanam://home/a#other", "anushtanam://user@home/a").forEach { assertNull(target(it)) }
    }
}
