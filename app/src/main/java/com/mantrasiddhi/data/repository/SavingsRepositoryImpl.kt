/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import com.mantrasiddhi.data.local.dao.SavingsDao
import com.mantrasiddhi.data.local.entity.SavingsEntity
import com.mantrasiddhi.domain.model.Savings
import com.mantrasiddhi.domain.repository.SavingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SavingsRepositoryImpl @Inject constructor(
    private val savingsDao: SavingsDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SavingsRepository {

    override suspend fun getSavingsForUser(userId: Long): Savings? = withContext(ioDispatcher) {
        savingsDao.getSavingsForUser(userId)?.toDomain()
    }

    override fun getSavingsFlowForUser(userId: Long): Flow<Savings?> {
        return savingsDao.getSavingsFlowForUser(userId)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)
    }

    override suspend fun updateSavings(savings: Savings) = withContext(ioDispatcher) {
        savingsDao.update(SavingsEntity.fromDomain(savings))
    }
}
