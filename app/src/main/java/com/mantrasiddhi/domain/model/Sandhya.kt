/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * The three daily Sandhya Vandan junctures (sandhi-kāla), each tied to a solar
 * event. The reminder for each fires a few minutes before the event.
 *
 * [requestCode] is a stable, distinct id so the three scheduled alarms never
 * overwrite one another and can be cancelled individually.
 */
enum class SandhyaPeriod(val requestCode: Int) {
    /** Prātaḥ-sandhyā — dawn, at sunrise. */
    PRATAH(2001),

    /** Mādhyāhnika-sandhyā — midday, at solar noon (the sun at zenith). */
    MADHYAHNA(2002),

    /** Sāyaṃ-sandhyā — dusk, at sunset. */
    SAYAM(2003)
}

/** Lifecycle of a single Sandhya occurrence. */
enum class SandhyaStatus {
    /** The reminder fired (a row is created at this point). */
    NOTIFIED,

    /** The sadhak confirmed they performed the anushtanam. */
    OBSERVED,

    /** The day passed without the user marking it done. */
    MISSED
}

/**
 * One scheduled Sandhya occurrence and its outcome. [dateKey] is the local
 * calendar day in `yyyyMMdd` form, used for day-bucketing and streaks so that
 * timezone/DST shifts never split or merge an anushtanam day.
 */
data class SandhyaLog(
    val id: Long,
    val userId: Long,
    val period: SandhyaPeriod,
    val scheduledEpochMillis: Long,
    val status: SandhyaStatus,
    val observedEpochMillis: Long?,
    val dateKey: String
)

/** Adherence for a single period across the user's whole history. */
data class PeriodAdherence(
    val period: SandhyaPeriod,
    val scheduled: Int,
    val observed: Int
) {
    val rate: Double get() = if (scheduled == 0) 0.0 else observed.toDouble() / scheduled
}

/** One calendar day in a trend series (gap-filled, so every day is present). */
data class TrendPoint(
    val dateKey: String,
    val dateMillis: Long,
    val scheduled: Int,
    val observed: Int
)

/**
 * Full adherence dashboard derived from the user's Sandhya logs.
 *
 * Two streak flavours are surfaced deliberately: "all three" (did the complete
 * trisandhyā) and "at least one" (showed up at all) are different motivational
 * signals.
 */
data class SandhyaAdherence(
    val totalScheduled: Int,
    val totalObserved: Int,
    val overallAdherenceRate: Double,
    val currentStreakAllThree: Int,
    val longestStreakAllThree: Int,
    val currentStreakAtLeastOne: Int,
    val longestStreakAtLeastOne: Int,
    val perPeriod: List<PeriodAdherence>,
    val onTimeCount: Int,
    val onTimeRate: Double,
    val avgPunctualityDeltaMinutes: Double,
    val last7Days: List<TrendPoint>,
    val last30Days: List<TrendPoint>,
    val mostConsistentPeriod: SandhyaPeriod?
) {
    companion object {
        /** Empty dashboard shown before any reminders have fired. */
        val EMPTY = SandhyaAdherence(
            totalScheduled = 0,
            totalObserved = 0,
            overallAdherenceRate = 0.0,
            currentStreakAllThree = 0,
            longestStreakAllThree = 0,
            currentStreakAtLeastOne = 0,
            longestStreakAtLeastOne = 0,
            perPeriod = SandhyaPeriod.entries.map { PeriodAdherence(it, 0, 0) },
            onTimeCount = 0,
            onTimeRate = 0.0,
            avgPunctualityDeltaMinutes = 0.0,
            last7Days = emptyList(),
            last30Days = emptyList(),
            mostConsistentPeriod = null
        )
    }
}
