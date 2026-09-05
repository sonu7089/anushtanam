/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

import java.time.LocalDate

/**
 * Per-day aggregate inside a [ProgressSummary].
 *
 * @property n Number of sessions recorded that day (correct and incorrect).
 * @property c Number of correct sessions that day.
 * @property s Sum of overall scores of that day's sessions (avg = s / n).
 */
data class DayAggregate(
    val n: Long,
    val c: Long,
    val s: Double
)

/**
 * Compact cloud mirror of a user's headline progress stats (`progress/{uid}` in
 * Firestore). Totals plus a rolling per-day map (capped at
 * [DAILY_KEY_CAP] most recent day keys) — enough to rebuild the Progress-tab
 * overview cards and 7-day charts after a reinstall or device switch. Detailed
 * session history stays device-local by design.
 *
 * Day keys are ISO local dates (`yyyy-MM-dd`, device timezone), so lexicographic
 * order equals chronological order.
 */
data class ProgressSummary(
    val totalSessions: Long,
    val correctSessions: Long,
    val scoreSum: Double,
    val bestScore: Double,
    val daily: Map<String, DayAggregate>
) {

    /** Mean overall score across all sessions; 0.0 when there are none. */
    val averageAccuracy: Double
        get() = if (totalSessions <= 0L) 0.0 else scoreSum / totalSessions

    /**
     * Consecutive days with at least one correct session, ending at [todayKey].
     * Mirrors the local streak semantics (GetRecitationStatsUseCase): a day counts
     * when it has any correct recitation, and a streak is still "alive" if today has
     * none yet but yesterday does — the walk then starts from yesterday. Capped at
     * [DAILY_KEY_CAP] since older days are pruned from the map anyway.
     */
    fun currentStreak(todayKey: String): Int {
        var day = LocalDate.parse(todayKey)
        if (correctOn(day) == 0L) {
            // Today empty so far: the streak may still continue from yesterday.
            day = day.minusDays(1)
        }
        var streak = 0
        while (streak < DAILY_KEY_CAP && correctOn(day) > 0L) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /**
     * Correct-session counts for the [lastNDays] ending at [todayKey], oldest first,
     * zero-filled for missing days. Counts CORRECT sessions only, matching the local
     * daily chart (GetDailyRecitationCountsUseCase counts `isCorrect` sessions).
     */
    fun dailyCounts(lastNDays: Int, todayKey: String): List<Pair<String, Int>> {
        val today = LocalDate.parse(todayKey)
        return ((lastNDays - 1) downTo 0).map { back ->
            val key = today.minusDays(back.toLong()).toString()
            key to (daily[key]?.c ?: 0L).toInt()
        }
    }

    /**
     * Mean overall score per day (`s / n`) for the [lastNDays] ending at [todayKey],
     * oldest first, `0.0` for days with no sessions. Averages ALL sessions of the day
     * (correct and incorrect), matching the local GetAccuracyTrendUseCase.
     */
    fun accuracyTrend(lastNDays: Int, todayKey: String): List<Pair<String, Double>> {
        val today = LocalDate.parse(todayKey)
        return ((lastNDays - 1) downTo 0).map { back ->
            val key = today.minusDays(back.toLong()).toString()
            val agg = daily[key]
            key to if (agg == null || agg.n <= 0L) 0.0 else agg.s / agg.n
        }
    }

    private fun correctOn(day: LocalDate): Long = daily[day.toString()]?.c ?: 0L

    companion object {
        /** Maximum number of day keys kept in [daily] (~60 days keeps the doc small). */
        const val DAILY_KEY_CAP = 60
    }
}
