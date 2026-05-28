/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.domain.model.Savings
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.RecitationRepository
import com.mantrasiddhi.domain.repository.SavingsRepository
import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.util.Calendar
import javax.inject.Inject

/**
 * Domain model for combined dashboard statistics.
 */
data class DashboardStats(
    val user: UserProfile,
    val todayRecitationCount: Int,
    val streakDays: Int,
    val activeTarget: Target?,
    val savings: Savings?,
    val childrenProgress: List<ChildProgress> = emptyList()
)

/**
 * UseCase to aggregate dashboard stats for the current user.
 */
class GetDashboardStatsUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val targetRepository: TargetRepository,
    private val savingsRepository: SavingsRepository,
    private val recitationRepository: RecitationRepository,
    private val getChildProgressUseCase: GetChildProgressUseCase
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(userId: Long): Flow<DashboardStats?> {
        return combine(
            targetRepository.getAllForUser(userId),
            savingsRepository.getSavingsFlowForUser(userId),
            recitationRepository.getSessionsForUser(userId),
            userRepository.getChildrenOf(userId)
        ) { targets, savings, sessions, children ->
            Quadruple(targets, savings, sessions, children)
        }.flatMapLatest { (targets, savings, sessions, children) ->
            flow {
                val userProfile = userRepository.getById(userId)
                if (userProfile != null) {
                    val activeTarget = targets.firstOrNull { !it.isCompleted }
                    val todayCount = sessions.filter { isToday(it.createdAt) && it.isCorrect }.size
                    val streak = calculateStreak(sessions)
                    
                    if (userProfile.profileType == com.mantrasiddhi.domain.model.ProfileType.PARENT && children.isNotEmpty()) {
                        val childProgressFlows = children.map { child ->
                            getChildProgressUseCase(child.id)
                        }
                        combine(childProgressFlows) { progresses ->
                            progresses.filterNotNull()
                        }.collect { progressList ->
                            emit(
                                DashboardStats(
                                    user = userProfile,
                                    todayRecitationCount = todayCount,
                                    streakDays = streak,
                                    activeTarget = activeTarget,
                                    savings = savings,
                                    childrenProgress = progressList
                                )
                            )
                        }
                    } else {
                        emit(
                            DashboardStats(
                                user = userProfile,
                                todayRecitationCount = todayCount,
                                streakDays = streak,
                                activeTarget = activeTarget,
                                savings = savings,
                                childrenProgress = emptyList()
                            )
                        )
                    }
                } else {
                    emit(null)
                }
            }
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    }

    private fun calculateStreak(sessions: List<com.mantrasiddhi.domain.model.RecitationSession>): Int {
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
            if (diff <= 24 * 60 * 60 * 1000L + 60000L) {
                streak++
                current = dates[i]
            } else {
                break
            }
        }
        return streak
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
