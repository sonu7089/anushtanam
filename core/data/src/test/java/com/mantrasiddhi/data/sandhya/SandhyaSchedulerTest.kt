package com.mantrasiddhi.data.sandhya

import android.app.AlarmManager
import android.content.Context
import com.mantrasiddhi.data.preferences.*
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.SandhyaLogRepository
import com.mantrasiddhi.domain.util.SandhyaDateKey
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SandhyaSchedulerTest {
    private val prefs = mockk<SandhyaPreferences>()
    private val logs = mockk<SandhyaLogRepository>()
    private val context = mockk<Context>()
    private val settings = SandhyaSettings(true, SandhyaRegions.DEFAULT, 7)
    private val now = LocalDate.of(2026, 9, 9).atStartOfDay(settings.zone()).toInstant().toEpochMilli()
    private fun scheduler(): SandhyaScheduler {
        every { context.getSystemService(Context.ALARM_SERVICE) } returns mockk<AlarmManager>()
        coEvery { prefs.delivered(any(), any(), any()) } returns 0L
        coEvery { prefs.deliveredDate(any(), any(), any()) } returns null
        coEvery { logs.getLogs(7) } returns emptyList()
        return SandhyaScheduler(context, prefs, logs)
    }
    @Test fun `all three periods have independent preparation and event times`() = runTest {
        val upcoming = scheduler().upcoming(settings, now)
        assertEquals(6, upcoming.size)
        SandhyaPeriod.entries.forEach { period ->
            val prepare = upcoming.getValue(period to SandhyaReminderStage.PREPARATION)
            val event = upcoming.getValue(period to SandhyaReminderStage.EVENT)
            assertEquals(event.eventMillis, prepare.eventMillis)
            assertEquals(900_000L, event.triggerMillis - prepare.triggerMillis)
        }
    }
    @Test fun `disabled master or missing owner schedules nothing`() = runTest {
        val scheduler = scheduler()
        assertTrue(scheduler.upcoming(settings.copy(enabled = false), now).isEmpty())
        assertTrue(scheduler.upcoming(settings.copy(userId = 0), now).isEmpty())
        assertTrue(scheduler.upcoming(settings.copy(preparationEnabled = false, eventEnabled = false), now).isEmpty())
    }
    @Test fun `turning off one stage leaves the other and late enablement keeps todays event`() = runTest {
        val scheduler = scheduler()
        val first = scheduler.upcoming(settings, now)
        val event = first.getValue(SandhyaPeriod.PRATAH to SandhyaReminderStage.EVENT).eventMillis
        val late = scheduler.upcoming(settings, event - 60_000)
        assertEquals(event, late.getValue(SandhyaPeriod.PRATAH to SandhyaReminderStage.EVENT).eventMillis)
        assertTrue(late.getValue(SandhyaPeriod.PRATAH to SandhyaReminderStage.PREPARATION).eventMillis > event)
        assertEquals(3, scheduler.upcoming(settings.copy(preparationEnabled = false), now).size)
    }
    @Test fun `observed and already delivered stages move to the next day`() = runTest {
        val scheduler = scheduler()
        val first = scheduler.upcoming(settings, now)
        val event = first.getValue(SandhyaPeriod.SAYAM to SandhyaReminderStage.EVENT).eventMillis
        coEvery { prefs.delivered(7, SandhyaPeriod.SAYAM, SandhyaReminderStage.EVENT) } returns event
        assertTrue(scheduler.upcoming(settings, now).getValue(SandhyaPeriod.SAYAM to SandhyaReminderStage.EVENT).eventMillis > event)
        coEvery { logs.getLogs(7) } returns listOf(SandhyaLog(1, 7, SandhyaPeriod.SAYAM, event,
            SandhyaStatus.OBSERVED, now, SandhyaDateKey.of(event, settings.zone())))
        assertTrue(scheduler.upcoming(settings, now).getValue(SandhyaPeriod.SAYAM to SandhyaReminderStage.PREPARATION).eventMillis > event)
    }
    @Test fun `a location change does not repeat a delivered stage for the same date`() = runTest {
        val scheduler = scheduler()
        val date = SandhyaDateKey.of(now, settings.zone())
        coEvery { prefs.deliveredDate(7, SandhyaPeriod.PRATAH, SandhyaReminderStage.PREPARATION) } returns date
        val next = scheduler.upcoming(settings, now).getValue(SandhyaPeriod.PRATAH to SandhyaReminderStage.PREPARATION)
        assertNotEquals(date, SandhyaDateKey.of(next.eventMillis, settings.zone()))
    }
}
