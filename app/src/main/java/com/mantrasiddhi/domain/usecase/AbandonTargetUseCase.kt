/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Savings
import com.mantrasiddhi.domain.repository.SavingsRepository
import com.mantrasiddhi.domain.repository.TargetRepository
import javax.inject.Inject

/**
 * Use case to abandon/delete the active target for a user, returning any locked money.
 */
class AbandonTargetUseCase @Inject constructor(
    private val targetRepository: TargetRepository,
    private val savingsRepository: SavingsRepository
) {
    suspend operator fun invoke(userId: Long): Boolean {
        val activeTarget = targetRepository.getActiveForUser(userId) ?: return false
        
        // Delete the active target
        targetRepository.delete(activeTarget.id)
        
        // Return locked money (meaning totalLocked is reduced, totalSaved/unlocked is unchanged)
        if (activeTarget.moneyAmount > 0.0) {
            val currentSavings = savingsRepository.getSavingsForUser(userId)
                ?: Savings(userId = userId)
            val updatedSavings = currentSavings.copy(
                totalLocked = maxOf(0.0, currentSavings.totalLocked - activeTarget.moneyAmount)
            )
            savingsRepository.updateSavings(updatedSavings)
        }
        return true
    }
}
