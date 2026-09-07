/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.TargetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * UseCase to retrieve all completed targets for a user.
 */
class GetCompletedTargetsUseCase @Inject constructor(
    private val targetRepository: TargetRepository
) {
    operator fun invoke(userId: Long): Flow<List<Target>> {
        return targetRepository.getAllForUser(userId).map { list ->
            list.filter { it.isCompleted }
        }
    }
}
