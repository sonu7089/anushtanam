/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Savings
import com.mantrasiddhi.domain.repository.SavingsRepository
import javax.inject.Inject

/**
 * UseCase to update a user's savings details.
 */
class UpdateSavingsUseCase @Inject constructor(
    private val savingsRepository: SavingsRepository
) {
    suspend operator fun invoke(savings: Savings) {
        savingsRepository.updateSavings(savings)
    }
}
