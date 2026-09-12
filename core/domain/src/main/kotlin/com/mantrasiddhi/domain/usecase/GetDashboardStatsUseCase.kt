/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.RecitationRepository
import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.mantrasiddhi.domain.model.currentStreak
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Domain model for combined dashboard statistics.
 */
data class DashboardStats(
    val user: UserProfile,
    val todayRecitationCount: Int,
    val streakDays: Int,
    val activeTarget: Target?,
    val childrenProgress: List<ChildProgress> = emptyList()
)

/** Only the current profile's visible dashboard data; child analytics belong to Family. */
class GetDashboardStatsUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val targetRepository: TargetRepository,
    private val recitationRepository: RecitationRepository
) {
    operator fun invoke(userId: Long): Flow<DashboardStats?> = combine(
        targetRepository.getAllForUser(userId),
        recitationRepository.observePracticeDays(userId),
        flow { emit(userRepository.getById(userId)) }
    ) { targets, days, user ->
        user?.let { DashboardStats(it, days.firstOrNull { day -> day.dateKey == LocalDate.now().toString() }?.count ?: 0,
            days.currentStreak(), targets.filter { target -> target.isActive }.maxWithOrNull(
                compareBy<Target> { target -> target.startedAtMillis }.thenBy { target -> target.stableId })) }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
}
