package com.mantrasiddhi.presentation

import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.sandhya.SandhyaReceiverEntryPoint
import com.mantrasiddhi.domain.diagnostics.WorkProbe
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in read-only fixture probe: retain the phone's settings and reconcile its real alarms. */
class ReminderMaintenanceTest {
    @Test fun unchangedInputsReuseAlarmsAndExplicitRecoveryRearmsThem() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("perfMaintenance") == "true")
        val entry = SandhyaReceiverEntryPoint.from(InstrumentationRegistry.getInstrumentation().targetContext)
        val settings = entry.preferences().current()
        assumeTrue(settings.userId > 0 && settings.enabled && settings.preparationEnabled && settings.eventEnabled)
        val probeEnabled = WorkProbe.enabled
        WorkProbe.enabled = true
        try {
            val scheduler = entry.scheduler()
            val expected = scheduler.upcoming(settings).size.toLong()
            assertEquals(6L, expected)
            fun writes() = WorkProbe.snapshot().first["reminder.alarm.write"] ?: 0L
            withContext(Dispatchers.Default) { scheduler.scheduleAll(force = true) }
            val first = writes()
            withContext(Dispatchers.Default) { repeat(5) { scheduler.scheduleAll() } }
            assertEquals(first, writes())
            withContext(Dispatchers.Default) { scheduler.scheduleAll(force = true) }
            assertEquals(first + expected, writes())
            assertEquals(settings, entry.preferences().current())
        } finally { WorkProbe.enabled = probeEnabled }
    }
}
