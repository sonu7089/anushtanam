/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.TargetRepository
import javax.inject.Inject

/**
 * UseCase to get the active target goal for a user.
 */
class GetActiveTargetUseCase @Inject constructor(
    private val targetRepository: TargetRepository
) {
    suspend operator fun invoke(userId: Long): Target? {
        return targetRepository.getActiveForUser(userId)
    }
}
