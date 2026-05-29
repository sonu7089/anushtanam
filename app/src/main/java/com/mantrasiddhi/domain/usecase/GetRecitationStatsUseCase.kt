/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

/**
 * Domain representation of aggregated overview stats for analytics.
 */
data class RecitationOverviewStats(
    val totalSessions: Int,
    val totalCorrectRecitations: Int,
    val averageAccuracy: Double,
    val currentStreak: Int,
    val bestSessionScore: Double
)

/**
 * UseCase to calculate recitation overview statistics.
 */
class GetRecitationStatsUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository
) {
    operator fun invoke(userId: Long): Flow<RecitationOverviewStats> {
        return recitationRepository.getSessionsForUser(userId).map { sessions ->
            if (sessions.isEmpty()) {
                RecitationOverviewStats(
                    totalSessions = 0,
                    totalCorrectRecitations = 0,
                    averageAccuracy = 0.0,
                    currentStreak = 0,
                    bestSessionScore = 0.0
                )
            } else {
                val total = sessions.size
                val correctSessions = sessions.filter { it.isCorrect }
                val totalCorrect = correctSessions.size
                val avgAccuracy = sessions.map { it.overallScore }.average()
                val bestScore = sessions.maxOfOrNull { it.overallScore } ?: 0.0
                val streak = calculateStreak(sessions)

                RecitationOverviewStats(
                    totalSessions = total,
                    totalCorrectRecitations = totalCorrect,
                    averageAccuracy = if (avgAccuracy.isNaN()) 0.0 else avgAccuracy,
                    currentStreak = streak,
                    bestSessionScore = bestScore
                )
            }
        }
    }

    private fun calculateStreak(sessions: List<RecitationSession>): Int {
        val correctSessions = sessions.filter { it.isCorrect }
        if (correctSessions.isEmpty()) return 0

        val dates = correctSessions.map {
            val cal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.distinct().sortedDescending()

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterday = today - 24 * 60 * 60 * 1000L

        if (dates.isEmpty() || (dates[0] != today && dates[0] != yesterday)) {
            return 0
        }

        var streak = 1
        var current = dates[0]
        for (i in 1 until dates.size) {
            val diff = current - dates[i]
            if (diff <= 24 * 60 * 60 * 1000L + 60000L) { // Allow 1 minute grace buffer
                streak++
                current = dates[i]
            } else {
                break
            }
        }
        return streak
    }
}
