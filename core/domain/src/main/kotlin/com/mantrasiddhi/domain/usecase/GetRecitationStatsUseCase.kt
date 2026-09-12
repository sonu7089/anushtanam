/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.mantrasiddhi.domain.model.currentStreak
import javax.inject.Inject

/**
 * Domain representation of aggregated overview stats for analytics.
 */
data class RecitationOverviewStats(
    val totalSessions: Int,
    val totalCorrectRecitations: Int,
    val averageAccuracy: Double,
    val currentStreak: Int,
    val bestSessionScore: Double,
    val performedJapa: String? = null
)

/**
 * UseCase to calculate recitation overview statistics.
 */
class GetRecitationStatsUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository
) {
    operator fun invoke(userId: Long): Flow<RecitationOverviewStats> = kotlinx.coroutines.flow.combine(
        recitationRepository.observeTotals(userId), recitationRepository.observePracticeDays(userId)
    ) { totals, days ->
        RecitationOverviewStats(totals.total, totals.correct, totals.average,
            days.currentStreak(), totals.best)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
}
