/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Why a word surfaced in the focused practice plan. */
enum class PracticeReason {
    /** Never practised yet. */
    NEW,

    /** Low average mastery. */
    WEAK,

    /** Mastered but not practised recently (spaced-repetition resurfacing). */
    OVERDUE
}

/**
 * A single word the learner should drill next.
 *
 * @property word The reference word (transliteration).
 * @property pada The 1-based pada the word belongs to.
 * @property masteryScore Average per-word score across the learner's history (`0.0..1.0`).
 * @property attempts How many sessions included this word.
 * @property lastPracticedAt Timestamp of the most recent attempt, or `null` if never.
 * @property reason Why this word was scheduled.
 */
data class PracticePlanItem(
    val word: String,
    val pada: Int,
    val masteryScore: Double,
    val attempts: Int,
    val lastPracticedAt: Long?,
    val reason: PracticeReason
)

/**
 * A focused drill: the learner's current weakest / most-overdue words.
 *
 * @property items The scheduled words, most-important first.
 */
data class PracticePlan(val items: List<PracticePlanItem>) {
    /** The distinct padas (lines) touched by the plan, useful for "practise line 3". */
    val padas: List<Int> get() = items.map { it.pada }.distinct().sorted()
}

/**
 * Builds a focused practice plan using simple spaced repetition (§4.4): it resurfaces
 * the words that are weakest, never attempted, or mastered-but-overdue, so practice
 * targets exactly what needs the most attention right now.
 */
class GetPracticePlanUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository
) {

    /**
     * @param userId The learner whose history drives the plan.
     * @param limit Maximum number of words to surface.
     */
    operator fun invoke(userId: Long, limit: Int = DEFAULT_LIMIT): Flow<PracticePlan> =
        recitationRepository.getSessionsForUser(userId).map { plan(it, System.currentTimeMillis(), limit) }

    companion object {
        private const val DEFAULT_LIMIT = 3

        /** Average score at/above which a word is considered mastered. */
        private const val MASTERED_THRESHOLD = 0.85

        /** Base spaced-repetition interval before a mastered word resurfaces (3 days). */
        private const val BASE_INTERVAL_MS = 3 * 24 * 60 * 60 * 1000L

        /** Relative weight of weakness vs. overdue-ness when ranking the plan. */
        private const val WEAKNESS_WEIGHT = 0.7
        private const val OVERDUE_WEIGHT = 0.3

        /**
         * Pure plan computation, testable without a clock or repository.
         *
         * @param sessions The learner's sessions (any order).
         * @param now Current time in millis, used for overdue calculations.
         * @param limit Maximum number of words to return.
         */
        fun plan(sessions: List<RecitationSession>, now: Long, limit: Int = DEFAULT_LIMIT): PracticePlan {
            val scored = GayatriMantra.words.mapNotNull { word ->
                val attemptsForWord = sessions.filter { it.wordScores.containsKey(word.transliteration) }
                val attempts = attemptsForWord.size
                val mastery = if (attempts == 0) 0.0
                    else attemptsForWord.sumOf { it.wordScores.getValue(word.transliteration) } / attempts
                val lastPracticed = attemptsForWord.maxOfOrNull { it.createdAt }
                val priority = priorityFor(attempts, mastery, lastPracticed, now) ?: return@mapNotNull null
                val item = PracticePlanItem(
                    word = word.transliteration,
                    pada = word.pada,
                    masteryScore = mastery,
                    attempts = attempts,
                    lastPracticedAt = lastPracticed,
                    reason = reasonFor(attempts, mastery)
                )
                item to priority
            }
            val items = scored.sortedByDescending { it.second }.take(limit).map { it.first }
            return PracticePlan(items)
        }

        /** Returns a ranking priority for a due word, or `null` when it is not due. */
        private fun priorityFor(attempts: Int, mastery: Double, lastPracticed: Long?, now: Long): Double? {
            if (attempts == 0) return Double.MAX_VALUE // Never tried: practise first.
            val weakness = 1.0 - mastery
            if (mastery < MASTERED_THRESHOLD) {
                return WEAKNESS_WEIGHT * weakness + OVERDUE_WEIGHT * overdueFraction(mastery, lastPracticed, now)
            }
            val overdue = overdueFraction(mastery, lastPracticed, now)
            if (overdue < 1.0) return null // Mastered and still fresh: skip.
            return WEAKNESS_WEIGHT * weakness + OVERDUE_WEIGHT * overdue
        }

        /**
         * Fraction of the spaced-repetition interval that has elapsed since the word was
         * last practised; `>= 1.0` means overdue. Mastered words get a longer interval.
         */
        private fun overdueFraction(mastery: Double, lastPracticed: Long?, now: Long): Double {
            if (lastPracticed == null) return Double.MAX_VALUE
            val interval = BASE_INTERVAL_MS * (1.0 + mastery) // 1x..2x base by mastery.
            return (now - lastPracticed).coerceAtLeast(0L) / interval
        }

        private fun reasonFor(attempts: Int, mastery: Double): PracticeReason = when {
            attempts == 0 -> PracticeReason.NEW
            mastery < MASTERED_THRESHOLD -> PracticeReason.WEAK
            else -> PracticeReason.OVERDUE
        }
    }
}
