/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgressSummaryTest {

    private val today = "2026-07-23"

    private fun summary(
        totalSessions: Long = 0L,
        correctSessions: Long = 0L,
        scoreSum: Double = 0.0,
        bestScore: Double = 0.0,
        daily: Map<String, DayAggregate> = emptyMap()
    ) = ProgressSummary(totalSessions, correctSessions, scoreSum, bestScore, daily)

    private fun day(c: Long, n: Long = c, s: Double = c.toDouble()) = DayAggregate(n = n, c = c, s = s)

    // --- averageAccuracy -------------------------------------------------------

    @Test
    fun `averageAccuracy is zero when there are no sessions`() {
        assertEquals(0.0, summary().averageAccuracy)
    }

    @Test
    fun `averageAccuracy is scoreSum over totalSessions`() {
        assertEquals(0.85, summary(totalSessions = 4L, scoreSum = 3.4).averageAccuracy, 1e-9)
    }

    // --- currentStreak ---------------------------------------------------------

    @Test
    fun `streak is zero when daily map is empty`() {
        assertEquals(0, summary().currentStreak(today))
    }

    @Test
    fun `streak counts consecutive correct days ending today`() {
        val s = summary(
            daily = mapOf(
                "2026-07-23" to day(c = 2),
                "2026-07-22" to day(c = 1),
                "2026-07-21" to day(c = 3)
            )
        )
        assertEquals(3, s.currentStreak(today))
    }

    @Test
    fun `streak counts from yesterday when today has no correct session yet`() {
        val s = summary(
            daily = mapOf(
                // Today exists but with zero correct: streak is still alive from yesterday.
                "2026-07-23" to day(c = 0, n = 2, s = 1.0),
                "2026-07-22" to day(c = 1),
                "2026-07-21" to day(c = 2)
            )
        )
        assertEquals(2, s.currentStreak(today))
    }

    @Test
    fun `streak counts from yesterday when today is absent`() {
        val s = summary(
            daily = mapOf(
                "2026-07-22" to day(c = 1),
                "2026-07-21" to day(c = 2)
            )
        )
        assertEquals(2, s.currentStreak(today))
    }

    @Test
    fun `streak is broken by a gap day`() {
        val s = summary(
            daily = mapOf(
                "2026-07-23" to day(c = 1),
                // 2026-07-22 missing -> the older run must not count.
                "2026-07-21" to day(c = 5),
                "2026-07-20" to day(c = 5)
            )
        )
        assertEquals(1, s.currentStreak(today))
    }

    @Test
    fun `streak is zero when the last correct day is before yesterday`() {
        val s = summary(daily = mapOf("2026-07-20" to day(c = 4)))
        assertEquals(0, s.currentStreak(today))
    }

    @Test
    fun `streak crosses a month boundary`() {
        val s = summary(
            daily = mapOf(
                "2026-07-01" to day(c = 1),
                "2026-06-30" to day(c = 1),
                "2026-06-29" to day(c = 1)
            )
        )
        assertEquals(3, s.currentStreak("2026-07-01"))
    }

    // --- dailyCounts -----------------------------------------------------------

    @Test
    fun `dailyCounts zero-fills missing days oldest to newest`() {
        val s = summary(
            daily = mapOf(
                "2026-07-23" to day(c = 3),
                "2026-07-21" to day(c = 1),
                // Outside the 7-day window: must not appear.
                "2026-07-01" to day(c = 9)
            )
        )
        val counts = s.dailyCounts(7, today)
        assertEquals(7, counts.size)
        assertEquals("2026-07-17" to 0, counts.first())
        assertEquals("2026-07-21" to 1, counts[4])
        assertEquals("2026-07-22" to 0, counts[5])
        assertEquals("2026-07-23" to 3, counts.last())
    }

    @Test
    fun `dailyCounts counts correct sessions only`() {
        // n=5 attempts but only c=2 correct: the chart mirrors the local
        // GetDailyRecitationCountsUseCase, which counts correct sessions.
        val s = summary(daily = mapOf("2026-07-23" to day(c = 2, n = 5, s = 3.0)))
        assertEquals(2, s.dailyCounts(1, today).single().second)
    }

    // --- accuracyTrend ---------------------------------------------------------

    @Test
    fun `accuracyTrend averages all sessions of the day and zero-fills empty days`() {
        val s = summary(
            daily = mapOf(
                // 4 sessions summing 3.2 -> 0.8 average (all sessions, not just correct).
                "2026-07-23" to day(c = 3, n = 4, s = 3.2),
                "2026-07-22" to day(c = 1, n = 2, s = 1.5)
            )
        )
        val trend = s.accuracyTrend(3, today)
        assertEquals(3, trend.size)
        assertEquals("2026-07-21" to 0.0, trend[0])
        assertEquals("2026-07-22", trend[1].first)
        assertEquals(0.75, trend[1].second, 1e-9)
        assertEquals("2026-07-23", trend[2].first)
        assertEquals(0.8, trend[2].second, 1e-9)
    }

    @Test
    fun `accuracyTrend is zero for a day with zero session count`() {
        val s = summary(daily = mapOf("2026-07-23" to day(c = 0, n = 0, s = 0.0)))
        assertEquals(0.0, s.accuracyTrend(1, today).single().second)
    }
}
