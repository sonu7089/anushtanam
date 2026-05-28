/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.Savings
import kotlinx.coroutines.flow.Flow

interface SavingsRepository {
    suspend fun getSavingsForUser(userId: Long): Savings?
    fun getSavingsFlowForUser(userId: Long): Flow<Savings?>
    suspend fun updateSavings(savings: Savings)
}
