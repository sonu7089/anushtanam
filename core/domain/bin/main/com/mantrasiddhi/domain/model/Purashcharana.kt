/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

import kotlin.math.ceil

/**
 * One stage on the Purashcharana path.
 *
 * The ladder exists so a sankalpa measured in millions of japa still has something
 * reachable in front of the sadhak. Only the count and the stage identity live here;
 * the display name and its plain-English gloss are string resources, because the
 * domain layer must stay free of UI copy.
 */
data class PurashcharanaMilestone(
    /** 0-based position on the ladder, assigned after filtering, always ascending. */
    val ordinal: Int,
    /** Verified japa count at which this stage is reached. */
    val japaCount: Long,
    val stage: PurashcharanaStage
)

/**
 * The named stages of a Purashcharana.
 *
 * The first four are fixed early landmarks (they arrive in the first weeks and months);
 * the last six are fractions of the full target, so they scale with the mantra length.
 */
enum class PurashcharanaStage {
    /** Arambha - the beginning, 1,008 japa. */
    ARAMBHA,

    /** Dasha sahasra - ten thousand japa. */
    DASHA_SAHASRA,

    /** Sahasra avritti - one thousand japa for every akshara (24,000 for the Gayatri). */
    SAHASRA_AVRITTI,

    /** Eka laksha - one lakh japa. */
    EKA_LAKSHA,

    /** Dashamsha - one tenth of the sankalpa. */
    DASHAMSHA,

    /** Chaturthamsha - one quarter. */
    CHATURTHAMSHA,

    /** Ardha - half. */
    ARDHA,

    /** Tripada - three quarters. */
    TRIPADA,

    /** Antima - the final stretch, nine tenths. */
    ANTIMA,

    /** Purnahuti - the full offering; the sankalpa is complete. */
    PURNAHUTI
}

/**
 * A sadhak's Purashcharana: the vow to recite one mantra one lakh (1,00,000) times for
 * every akshara in it. For the Gayatri (24 aksharas) that is 24,00,000 japa, which is a
 * multi-year sadhana — every field here is sized and phrased for that horizon.
 *
 * Counts include recorded japa and accepted NORMAL recitations. Learning scores stay separate.
 *
 * A completed Purashcharana keeps [isActive] true and simply carries a
 * [completedAtMillis]; the UI decides what to show from [isCompleted]. [isActive] is
 * false only when the sadhak has withdrawn, and even then the row is kept as history.
 */
data class Purashcharana(
    val id: Long = 0L,
    val userId: Long,
    /**
     * Id of the mantra under sankalpa. Two vows for different mantras are different vows
     * even when they happen to be the same length, so the cloud reconcile compares this
     * before it merges anything.
     */
    val mantraId: String,
    /** Aksharas in the mantra under sankalpa (24 for the Gayatri). */
    val mantraAksharaCount: Int,
    val targetJapaCount: Long,
    val currentJapaCount: Long,
    val startedAtMillis: Long,
    /** Set the moment the target count is reached; `null` while still in progress. */
    val completedAtMillis: Long? = null,
    val isActive: Boolean = true,
    val stableId: String = java.util.UUID.randomUUID().toString(),
    val endedAtMillis: Long? = null,
    val baselineCount: Long = currentJapaCount
) {

    /** Fraction of the sankalpa completed, in `0f..1f`. */
    val progress: Float
        get() = if (targetJapaCount > 0L) {
            (currentJapaCount.toFloat() / targetJapaCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    /** Japa still to be recited; never negative. */
    val remainingJapaCount: Long
        get() = (targetJapaCount - currentJapaCount).coerceAtLeast(0L)

    /**
     * True once the sankalpa is fulfilled. The count is checked as well as the stamp so
     * a Purnahuti is recognised even if the completion stamp could not be written.
     */
    val isCompleted: Boolean
        get() = completedAtMillis != null || currentJapaCount >= targetJapaCount

    /**
     * 1-based day of the sankalpa: the day it was taken is day 1, the next calendar-day
     * worth of elapsed time is day 2, and so on. Clamped to 1 so a clock that has moved
     * backwards (timezone change, manual clock set) can never show day 0 or a negative.
     */
    fun dayNumber(nowMillis: Long): Int {
        val elapsed = nowMillis - startedAtMillis
        if (elapsed <= 0L) return 1
        return (elapsed / MILLIS_PER_DAY + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Milestones already crossed, oldest first. */
    fun reachedMilestones(): List<PurashcharanaMilestone> =
        PurashcharanaPath.milestonesFor(mantraAksharaCount).filter { it.japaCount <= currentJapaCount }

    /** The next stage ahead, or `null` when every stage has been crossed. */
    fun nextMilestone(): PurashcharanaMilestone? =
        PurashcharanaPath.milestonesFor(mantraAksharaCount).firstOrNull { it.japaCount > currentJapaCount }

    /**
     * Verified japa per day so far, averaged over the elapsed days of the sankalpa.
     *
     * Returns `null` when nothing has been verified yet — with zero japa there is no
     * pace to speak of, and showing "0 a day" alongside an infinite ETA would only
     * discourage someone on their first morning.
     */
    fun japaPerDay(nowMillis: Long): Double? {
        if (currentJapaCount <= 0L) return null
        return currentJapaCount.toDouble() / dayNumber(nowMillis).toDouble()
    }

    /**
     * Whole days still to go at the current pace, rounded up.
     *
     * `null` when there is no pace yet (see [japaPerDay]) or the sankalpa is already
     * complete — in both cases there is nothing honest to project.
     */
    fun projectedDaysRemaining(nowMillis: Long): Long? {
        if (isCompleted) return null
        val perDay = japaPerDay(nowMillis) ?: return null
        if (perDay <= 0.0) return null
        return ceil(remainingJapaCount.toDouble() / perDay).toLong()
    }

    private companion object {
        const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
    }
}

/**
 * The arithmetic of the Purashcharana path, kept out of [Purashcharana] so the ladder
 * can be asked for before anyone has enrolled (the "what is this?" screen needs it).
 *
 * The ladder is derived from the akshara count rather than hardcoded for the Gayatri:
 * if the app ever carries a second mantra, a shorter one still gets a sane sequence of
 * stages instead of landmarks that sit past the finish line.
 */
object PurashcharanaPath {

    /** One lakh japa per akshara — the rule the whole practice rests on. */
    const val JAPA_PER_AKSHARA: Long = 100_000L

    /** Total japa a Purashcharana of an [aksharaCount]-syllable mantra asks for. */
    fun targetFor(aksharaCount: Int): Long =
        aksharaCount.coerceAtLeast(0).toLong() * JAPA_PER_AKSHARA

    /**
     * The milestone ladder for an [aksharaCount]-syllable mantra, strictly ascending.
     *
     * Four fixed early stages (1,008; 10,000; one thousand japa per akshara; one lakh) carry
     * the sadhak through the first stretch, then six fractional stages (10%, 25%, 50%,
     * 75%, 90%, 100%) carry them to Purnahuti. An early stage that would land on or past
     * the first fractional stage is dropped, as is any duplicate count, so a short mantra
     * cannot produce stages that collide or run backwards.
     */
    fun milestonesFor(aksharaCount: Int): List<PurashcharanaMilestone> {
        val target = targetFor(aksharaCount)
        if (target <= 0L) return emptyList()

        val fractional = listOf(
            target / 10L to PurashcharanaStage.DASHAMSHA,
            target / 4L to PurashcharanaStage.CHATURTHAMSHA,
            target / 2L to PurashcharanaStage.ARDHA,
            target * 3L / 4L to PurashcharanaStage.TRIPADA,
            target * 9L / 10L to PurashcharanaStage.ANTIMA,
            target to PurashcharanaStage.PURNAHUTI
        )
        val firstFractional = fractional.first().first

        // Canonical order matters: when two early stages land on the same count (a
        // 10-akshara mantra puts sahasra avritti exactly on 10,000) the earlier-declared
        // one wins, so the sadhak sees the better-known landmark.
        val early = listOf(
            1_008L to PurashcharanaStage.ARAMBHA,
            10_000L to PurashcharanaStage.DASHA_SAHASRA,
            aksharaCount.toLong() * 1_000L to PurashcharanaStage.SAHASRA_AVRITTI,
            JAPA_PER_AKSHARA to PurashcharanaStage.EKA_LAKSHA
        )
        val keptEarly = mutableListOf<Pair<Long, PurashcharanaStage>>()
        early.forEach { candidate ->
            val alreadyTaken = keptEarly.any { it.first == candidate.first }
            if (candidate.first < firstFractional && !alreadyTaken) keptEarly.add(candidate)
        }

        return (keptEarly.sortedBy { it.first } + fractional)
            .fold(mutableListOf<PurashcharanaMilestone>()) { acc, (count, stage) ->
                // Final guard: keep the ladder strictly increasing whatever the inputs.
                if (acc.isEmpty() || count > acc.last().japaCount) {
                    acc.add(PurashcharanaMilestone(acc.size, count, stage))
                }
                acc
            }
    }
}
