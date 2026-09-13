package com.mantrasiddhi.domain.util

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarTimeTest {
    @Test fun `idle expiry has only initial and exact boundary emissions`() = runTest {
        val clock = CalendarTime()
        val values = mutableListOf<Long>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            clock.deadlines({ if (it < 300_000) 300_000 else null }, { testScheduler.currentTime }).toList(values)
        }
        advanceTimeBy(299_999); runCurrent()
        assertEquals(listOf(0L), values)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(0L, 300_000L), values)
        advanceTimeBy(600_000); runCurrent()
        assertEquals(2, values.size)
        job.cancel()
    }

    @Test fun `clock jumps cancel old wait and hidden collectors stop`() = runTest {
        val clock = CalendarTime()
        var wall = 100L
        val values = mutableListOf<Long>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            clock.deadlines({ if (it < 1_000) 1_000 else null }, { wall }).toList(values)
        }
        wall = 2_000; clock.invalidate(); runCurrent()
        assertEquals(listOf(100L, 2_000L), values)
        wall = 50; clock.invalidate(); runCurrent()
        assertEquals(50L, values.last())
        job.cancel(); wall = 3_000; clock.invalidate(); advanceTimeBy(2_000); runCurrent()
        assertEquals(3, values.size)
        assertEquals(3_000L, clock.deadlines({ null }, { wall }).first())
    }

    @Test fun `midnight respects short and long daylight saving days`() {
        val zone = ZoneId.of("America/New_York")
        for ((date, hours) in listOf(LocalDate.of(2026, 3, 8) to 23, LocalDate.of(2026, 11, 1) to 25)) {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            assertEquals(hours * 3_600_000L, CalendarTime.nextMidnight(start, zone) - start)
        }
    }
}
