/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Savings
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.SavingsRepository
import com.mantrasiddhi.domain.repository.TargetRepository
import javax.inject.Inject

/**
 * Use case to create a new recitation target for a user.
 *
 * Validates that the user has no active target.
 * Locks the designated virtual money amount in the savings system.
 */
class CreateTargetUseCase @Inject constructor(
    private val targetRepository: TargetRepository,
    private val savingsRepository: SavingsRepository
) {
    suspend operator fun invoke(target: Target): Result<Long> {
        val activeTarget = targetRepository.getActiveForUser(target.userId)
        if (activeTarget != null) {
            return Result.failure(IllegalStateException("An active target already exists for this profile."))
        }
        
        val id = targetRepository.create(target)
        
        if (target.moneyAmount > 0.0) {
            val currentSavings = savingsRepository.getSavingsForUser(target.userId)
                ?: Savings(userId = target.userId)
            val updatedSavings = currentSavings.copy(
                totalLocked = currentSavings.totalLocked + target.moneyAmount
            )
            savingsRepository.updateSavings(updatedSavings)
        }
        return Result.success(id)
    }
}
