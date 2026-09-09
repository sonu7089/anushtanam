package com.mantrasiddhi.presentation

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.sandhya.*
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.util.SandhyaDateKey
import com.mantrasiddhi.presentation.navigation.SandhyaReminderTarget
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class SandhyaReminderNotificationTest {
    @Test fun stagesHaveDifferentContentAndDistinctImmutableDestinations() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fun postedContaining(text: String): Notification {
            val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                manager.activeNotifications.firstOrNull { it.id == SandhyaPeriod.SAYAM.requestCode &&
                    it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains(text) }
                    ?.let { return it.notification }
                Thread.sleep(50)
            }
            throw AssertionError("Notification did not appear: $text")
        }
        val notifier = SandhyaNotifier(context)
        notifier.ensureChannel()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        try {
            val now = System.currentTimeMillis()
            val zone = ZoneId.of("Asia/Kolkata")
            val date = SandhyaDateKey.of(now, zone)
            assertTrue(notifier.showReminder(SandhyaPeriod.SAYAM, now + 900_000, zone, date, 7, SandhyaReminderStage.PREPARATION))
            val preparation = postedContaining("Take a little time to get ready")
            assertTrue(preparation.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Take a little time to get ready"))
            assertTrue(preparation.contentIntent.isImmutable)
            assertTrue(notifier.showReminder(SandhyaPeriod.SAYAM, now, zone, date, 7, SandhyaReminderStage.EVENT))
            val event = postedContaining("When you finish")
            assertTrue(event.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("When you finish"))
            assertNotEquals(preparation.contentIntent, event.contentIntent)
            assertTrue(event.contentIntent.isImmutable)
            assertTrue(notifier.showReminder(SandhyaPeriod.SAYAM, now - 300_000, zone, date, 7, SandhyaReminderStage.EVENT))
            assertTrue(postedContaining("was at")
                .extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("was at"))
        } finally {
            notifier.cancel(SandhyaPeriod.SAYAM)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
    @Test fun destinationCarriesOwnerDatePeriodAndStageAndRejectsMalformedInput() {
        val intent = Intent(SandhyaContract.ACTION_OPEN)
            .putExtra(SandhyaContract.EXTRA_USER_ID, 7L)
            .putExtra(SandhyaContract.EXTRA_DATE_KEY, "20260907")
            .putExtra(SandhyaContract.EXTRA_PERIOD, "SAYAM")
            .putExtra(SandhyaContract.EXTRA_STAGE, "EVENT")
        val target = SandhyaReminderTarget.from(intent)!!
        assertEquals(7L, target.userId)
        assertEquals("2026-09-07", target.date.toString())
        assertEquals(SandhyaPeriod.SAYAM, target.period)
        assertEquals(SandhyaReminderStage.EVENT, target.stage)
        assertNull(SandhyaReminderTarget.from(Intent()))
        assertNull(SandhyaReminderTarget.from(intent.putExtra(SandhyaContract.EXTRA_USER_ID, 0L)))
    }
}
