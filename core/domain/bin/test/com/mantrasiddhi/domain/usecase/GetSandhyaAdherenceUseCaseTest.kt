/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.SandhyaLog
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.SandhyaStatus
import com.mantrasiddhi.domain.util.SandhyaDateKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class GetSandhyaAdherenceUseCaseTest {

    private val today = LocalDate.of(2025, 6, 15)
    private val zone = ZoneId.of("UTC")
    private val base = 1_700_000_000_000L

    private fun log(
        dayOffset: Int,
        period: SandhyaPeriod,
        status: SandhyaStatus,
        observedDeltaMs: Long? = 0L
    ): SandhyaLog {
        val date = today.minusDays(dayOffset.toLong())
        val scheduled = base + dayOffset * 86_400_000L + period.ordinal * 3_600_000L
        val observed =
            if (status == SandhyaStatus.OBSERVED && observedDeltaMs != null) scheduled + observedDeltaMs else null
        return SandhyaLog(
            id = 0,
            userId = 1,
            period = period,
            scheduledEpochMillis = scheduled,
            status = status,
            observedEpochMillis = observed,
            dateKey = SandhyaDateKey.of(date)
        )
    }

    private fun compute(logs: List<SandhyaLog>) =
        GetSandhyaAdherenceUseCase.compute(logs, today, zone)

    @Test
    fun `empty logs return empty adherence`() {
        val result = compute(emptyList())
        assertEquals(0, result.totalScheduled)
        assertEquals(0, result.totalObserved)
        assertEquals(0.0, result.overallAdherenceRate)
    }

    @Test
    fun `overall adherence is observed over scheduled`() {
        val logs = listOf(
            log(0, SandhyaPeriod.PRATAH, SandhyaStatus.OBSERVED),
            log(0, SandhyaPeriod.MADHYAHNA, SandhyaStatus.OBSERVED),
            log(0, SandhyaPeriod.SAYAM, SandhyaStatus.MISSED)
        )
        val result = compute(logs)
        assertEquals(3, result.totalScheduled)
        assertEquals(2, result.totalObserved)
        assertEquals(2.0 / 3.0, result.overallAdherenceRate, 1e-9)
    }

    @Test
    fun `full streak counts consecutive all-three days including today`() {
        val logs = (0..2).flatMap { offset ->
            SandhyaPeriod.entries.map { log(offset, it, SandhyaStatus.OBSERVED) }
        }
        val result = compute(logs)
        assertEquals(3, result.currentStreakAllThree)
        assertEquals(3, result.longestStreakAllThree)
    }

    @Test
    fun `at-least-one streak uses yesterday fallback when today is empty`() {
        // Today has nothing; the previous 5 days each have one observance.
        val logs = (1..5).map { offset -> log(offset, SandhyaPeriod.PRATAH, SandhyaStatus.OBSERVED) }
        val result = compute(logs)
        assertEquals(5, result.currentStreakAtLeastOne)
        assertEquals(0, result.currentStreakAllThree)
    }

    @Test
    fun `longest streak ignores gaps`() {
        // Observed days at offsets 0,1 (run of 2) and 3,4,5 (run of 3); gap at offset 2.
        val offsets = listOf(0, 1, 3, 4, 5)
        val logs = offsets.map { log(it, SandhyaPeriod.PRATAH, SandhyaStatus.OBSERVED) }
        val result = compute(logs)
        assertEquals(3, result.longestStreakAtLeastOne)
        assertEquals(2, result.currentStreakAtLeastOne)
    }

    @Test
    fun `per period always has three entries in fixed order`() {
        val logs = listOf(log(0, SandhyaPeriod.SAYAM, SandhyaStatus.OBSERVED))
        val result = compute(logs)
        assertEquals(3, result.perPeriod.size)
        assertEquals(SandhyaPeriod.PRATAH, result.perPeriod[0].period)
        assertEquals(SandhyaPeriod.MADHYAHNA, result.perPeriod[1].period)
        assertEquals(SandhyaPeriod.SAYAM, result.perPeriod[2].period)
        assertEquals(1, result.perPeriod[2].observed)
        assertEquals(0, result.perPeriod[0].scheduled)
    }

    @Test
    fun `punctuality computes on-time count and signed average delta`() {
        val logs = listOf(
            log(0, SandhyaPeriod.PRATAH, SandhyaStatus.OBSERVED, 2 * 60_000L),    // 2 min late
            log(1, SandhyaPeriod.MADHYAHNA, SandhyaStatus.OBSERVED, -1 * 60_000L), // 1 min early
            log(2, SandhyaPeriod.SAYAM, SandhyaStatus.OBSERVED, 20 * 60_000L)      // 20 min late
        )
        val result = compute(logs)
        assertEquals(2, result.onTimeCount)
        assertEquals(2.0 / 3.0, result.onTimeRate, 1e-9)
        assertEquals(7.0, result.avgPunctualityDeltaMinutes, 1e-6)
    }

    @Test
    fun `most consistent period respects minimum sample guard`() {
        val logs = buildList {
            // PRATAH: 6 scheduled, 6 observed -> rate 1.0, eligible.
            repeat(6) { add(log(it, SandhyaPeriod.PRATAH, SandhyaStatus.OBSERVED)) }
            // MADHYAHNA: 6 scheduled, 3 observed -> rate 0.5, eligible.
            repeat(3) { add(log(it, SandhyaPeriod.MADHYAHNA, SandhyaStatus.OBSERVED)) }
            repeat(3) { add(log(it + 3, SandhyaPeriod.MADHYAHNA, SandhyaStatus.MISSED)) }
            // SAYAM: 4 scheduled, 4 observed -> rate 1.0 but below the 5-sample guard.
            repeat(4) { add(log(it, SandhyaPeriod.SAYAM, SandhyaStatus.OBSERVED)) }
        }
        val result = compute(logs)
        assertEquals(SandhyaPeriod.PRATAH, result.mostConsistentPeriod)
    }

    @Test
    fun `most consistent period is null without enough samples`() {
        val logs = listOf(log(0, SandhyaPeriod.PRATAH, SandhyaStatus.OBSERVED))
        assertNull(compute(logs).mostConsistentPeriod)
    }

    @Test
    fun `trend series are gap filled to fixed lengths`() {
        val logs = listOf(log(0, SandhyaPeriod.PRATAH, SandhyaStatus.OBSERVED))
        val result = compute(logs)
        assertEquals(7, result.last7Days.size)
        assertEquals(30, result.last30Days.size)
        // The most recent point is today and reflects the single observance.
        assertEquals(1, result.last7Days.last().observed)
    }
}
