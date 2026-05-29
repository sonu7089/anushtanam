/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Savings
import com.mantrasiddhi.domain.repository.SavingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase to retrieve the virtual savings details flow for a user.
 */
class GetSavingsUseCase @Inject constructor(
    private val savingsRepository: SavingsRepository
) {
    operator fun invoke(userId: Long): Flow<Savings?> {
        return savingsRepository.getSavingsFlowForUser(userId)
    }
}
