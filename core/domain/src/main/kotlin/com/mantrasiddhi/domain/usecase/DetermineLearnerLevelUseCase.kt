/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.LearnerLevel
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Derives a learner's adaptive difficulty [LearnerLevel] from their mastery history.
 *
 * The level shifts the evaluation gate (§4.3): a beginner is judged leniently so they
 * can build confidence, while an advanced reciter is held to a stricter pronunciation
 * standard. It is intentionally based on a stable aggregate (recent average overall
 * score plus enough completed sessions) so the level does not flip on a single result.
 */
class DetermineLearnerLevelUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository
) {

    /**
     * Computes the current [LearnerLevel] for [userId] from their stored sessions.
     */
    suspend operator fun invoke(userId: Long): LearnerLevel =
        levelFor(recitationRepository.getRecentSessionsForUser(userId, RECENT_WINDOW).first())

    /**
     * Observes the [LearnerLevel] for [userId] as their history changes.
     */
    fun observe(userId: Long): Flow<LearnerLevel> =
        recitationRepository.getRecentSessionsForUser(userId, RECENT_WINDOW).map { levelFor(it) }

    companion object {
        /** Below this many sessions the learner is always treated as a beginner. */
        private const val BEGINNER_SESSION_FLOOR = 5

        /** Minimum sessions before a learner can be classified advanced. */
        private const val ADVANCED_SESSION_FLOOR = 15

        /** Number of most-recent sessions averaged to gauge current ability. */
        private const val RECENT_WINDOW = 20

        /** Recent-average overall score at/above which a learner is advanced. */
        private const val ADVANCED_AVG = 0.90

        /** Recent-average overall score at/above which a learner is intermediate. */
        private const val INTERMEDIATE_AVG = 0.75

        /**
         * Pure classification of a session history into a [LearnerLevel].
         *
         * @param sessions The learner's recitation sessions (order independent; the
         *   most recent [RECENT_WINDOW] by `createdAt` are averaged).
         */
        fun levelFor(sessions: List<RecitationSession>): LearnerLevel {
            if (sessions.size < BEGINNER_SESSION_FLOOR) return LearnerLevel.BEGINNER
            val recentAverage = sessions
                .sortedByDescending { it.createdAt }
                .take(RECENT_WINDOW)
                .map { it.overallScore }
                .average()
            return when {
                recentAverage >= ADVANCED_AVG && sessions.size >= ADVANCED_SESSION_FLOOR -> LearnerLevel.ADVANCED
                recentAverage >= INTERMEDIATE_AVG -> LearnerLevel.INTERMEDIATE
                else -> LearnerLevel.BEGINNER
            }
        }
    }
}
