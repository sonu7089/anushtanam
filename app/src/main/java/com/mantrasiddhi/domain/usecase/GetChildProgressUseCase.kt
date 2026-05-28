/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.domain.repository.RecitationRepository
import com.mantrasiddhi.domain.repository.SavingsRepository
import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * UseCase to retrieve and aggregate progress details for a child profile.
 */
class GetChildProgressUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val targetRepository: TargetRepository,
    private val savingsRepository: SavingsRepository,
    private val recitationRepository: RecitationRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(childId: Long): Flow<ChildProgress?> {
        return combine(
            targetRepository.getAllForUser(childId),
            savingsRepository.getSavingsFlowForUser(childId),
            recitationRepository.getSessionsForUser(childId)
        ) { targets, savings, sessions ->
            Triple(targets, savings, sessions)
        }.flatMapLatest { (targets, savings, sessions) ->
            flow {
                val childProfile = userRepository.getById(childId)
                if (childProfile != null) {
                    val activeTarget = targets.firstOrNull { !it.isCompleted }
                    val totalRecitations = sessions.count { it.isCorrect }
                    emit(
                        ChildProgress(
                            child = childProfile,
                            activeTarget = activeTarget,
                            totalRecitations = totalRecitations,
                            savings = savings,
                            recentSessions = sessions
                        )
                    )
                } else {
                    emit(null)
                }
            }
        }
    }
}
