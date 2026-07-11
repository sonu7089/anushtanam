/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.PeriodAdherence
import com.mantrasiddhi.domain.model.SandhyaAdherence
import com.mantrasiddhi.domain.model.SandhyaLog
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.SandhyaStatus
import com.mantrasiddhi.domain.model.TrendPoint
import com.mantrasiddhi.domain.repository.SandhyaLogRepository
import com.mantrasiddhi.domain.util.SandhyaDateKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs

/**
 * Computes the Sandhya adherence dashboard from a user's logs.
 *
 * The whole computation is pure and in-memory ([compute]); at three rows per day
 * the dataset is tiny, so SQL aggregation buys nothing and a pure function is far
 * easier to unit-test. This mirrors how [com.mantrasiddhi.data.repository.RecitationRepositoryImpl]
 * already aggregates recitation stats in Kotlin.
 */
class GetSandhyaAdherenceUseCase @Inject constructor(
    private val repository: SandhyaLogRepository
) {

    operator fun invoke(userId: Long): Flow<SandhyaAdherence> =
        repository.observeLogs(userId).map { compute(it) }

    companion object {
        /** A ritual counts as "on time" if performed within this window of the cue. */
        const val ON_TIME_WINDOW_MIN = 10L

        /** A period needs at least this many scheduled occurrences to win "most consistent". */
        const val MIN_SAMPLE_FOR_CONSISTENCY = 5

        private const val ON_TIME_WINDOW_MS = ON_TIME_WINDOW_MIN * 60_000L

        fun compute(
            logs: List<SandhyaLog>,
            today: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault()
        ): SandhyaAdherence {
            if (logs.isEmpty()) return SandhyaAdherence.EMPTY

            val observed = logs.filter { it.status == SandhyaStatus.OBSERVED }
            val totalScheduled = logs.size
            val totalObserved = observed.size

            // Per-period breakdown, always in PRATAH/MADHYAHNA/SAYAM order.
            val perPeriod = SandhyaPeriod.entries.map { period ->
                PeriodAdherence(
                    period = period,
                    scheduled = logs.count { it.period == period },
                    observed = observed.count { it.period == period }
                )
            }

            // Distinct observed periods per day, for the two streak flavours.
            val observedPeriodsByDay: Map<String, Int> = observed
                .groupBy { it.dateKey }
                .mapValues { (_, dayLogs) -> dayLogs.map { it.period }.distinct().size }
            val allThreeDays = observedPeriodsByDay.filterValues { it >= 3 }.keys
            val atLeastOneDays = observedPeriodsByDay.filterValues { it >= 1 }.keys

            // Punctuality over observed rows that carry an observation timestamp.
            val deltas = observed.mapNotNull { it.observedEpochMillis?.minus(it.scheduledEpochMillis) }
            val onTimeCount = deltas.count { abs(it) <= ON_TIME_WINDOW_MS }
            val avgDeltaMin = if (deltas.isEmpty()) 0.0 else deltas.average() / 60_000.0

            val mostConsistent = perPeriod
                .filter { it.scheduled >= MIN_SAMPLE_FOR_CONSISTENCY }
                .maxWithOrNull(compareBy<PeriodAdherence>({ it.rate }, { it.observed }))
                ?.period

            return SandhyaAdherence(
                totalScheduled = totalScheduled,
                totalObserved = totalObserved,
                overallAdherenceRate = if (totalScheduled == 0) 0.0 else totalObserved.toDouble() / totalScheduled,
                currentStreakAllThree = currentStreak(allThreeDays, today),
                longestStreakAllThree = longestStreak(allThreeDays),
                currentStreakAtLeastOne = currentStreak(atLeastOneDays, today),
                longestStreakAtLeastOne = longestStreak(atLeastOneDays),
                perPeriod = perPeriod,
                onTimeCount = onTimeCount,
                // Denominator is the population punctuality is measured over (observed rows
                // that carry a timestamp), matching the numerator.
                onTimeRate = if (deltas.isEmpty()) 0.0 else onTimeCount.toDouble() / deltas.size,
                avgPunctualityDeltaMinutes = avgDeltaMin,
                last7Days = trend(logs, observed, today, zone, 7),
                last30Days = trend(logs, observed, today, zone, 30),
                mostConsistentPeriod = mostConsistent
            )
        }

        /** Longest run of consecutive calendar days present in [days]. */
        private fun longestStreak(days: Set<String>): Int {
            if (days.isEmpty()) return 0
            val sorted = days.sorted() // yyyyMMdd sorts chronologically
            var best = 1
            var run = 1
            for (i in 1 until sorted.size) {
                val prev = SandhyaDateKey.parse(sorted[i - 1])
                val cur = SandhyaDateKey.parse(sorted[i])
                run = if (prev.plusDays(1) == cur) run + 1 else 1
                if (run > best) best = run
            }
            return best
        }

        /**
         * Run of consecutive days ending today. If today has no qualifying row yet
         * (reminders may not have fired), counting starts from yesterday so an
         * in-progress day doesn't zero the streak.
         */
        private fun currentStreak(days: Set<String>, today: LocalDate): Int {
            if (days.isEmpty()) return 0
            var anchor = if (days.contains(SandhyaDateKey.of(today))) today else today.minusDays(1)
            var count = 0
            while (days.contains(SandhyaDateKey.of(anchor))) {
                count++
                anchor = anchor.minusDays(1)
            }
            return count
        }

        /** Gap-filled per-day series for the last [days] days, ending today. */
        private fun trend(
            logs: List<SandhyaLog>,
            observed: List<SandhyaLog>,
            today: LocalDate,
            zone: ZoneId,
            days: Long
        ): List<TrendPoint> {
            val scheduledByDay = logs.groupingBy { it.dateKey }.eachCount()
            val observedByDay = observed.groupingBy { it.dateKey }.eachCount()
            val start = today.minusDays(days - 1)
            return (0 until days).map { offset ->
                val date = start.plusDays(offset)
                val key = SandhyaDateKey.of(date)
                TrendPoint(
                    dateKey = key,
                    dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                    scheduled = scheduledByDay[key] ?: 0,
                    observed = observedByDay[key] ?: 0
                )
            }
        }
    }
}
