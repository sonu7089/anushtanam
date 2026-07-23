/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.TargetRepository
import javax.inject.Inject

/**
 * Use case to increment the recitation count for a user's active target.
 *
 * If the target count is reached, it marks the target as completed. The reward
 * is simply considered earned at that point — there is no money ledger to update.
 */
class IncrementTargetUseCase @Inject constructor(
    private val targetRepository: TargetRepository
) {

    /**
     * Increments the active target recitation count for the specified user.
     *
     * @param userId The ID of the user whose active target should be updated.
     * @return The updated target, or null if no active target exists.
     */
    suspend operator fun invoke(userId: Long): Target? {
        val activeTarget = targetRepository.getActiveForUser(userId) ?: return null
        val newCount = activeTarget.currentRecitationCount + 1
        val isCompleted = newCount >= activeTarget.targetRecitationCount
        val updatedTarget = activeTarget.copy(
            currentRecitationCount = newCount,
            isCompleted = isCompleted
        )
        targetRepository.update(updatedTarget)
        return updatedTarget
    }
}
