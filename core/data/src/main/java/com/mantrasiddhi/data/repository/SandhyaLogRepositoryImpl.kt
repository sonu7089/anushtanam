/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import com.mantrasiddhi.data.local.dao.SandhyaLogDao
import com.mantrasiddhi.data.local.entity.SandhyaLogEntity
import com.mantrasiddhi.domain.model.SandhyaLog
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.SandhyaStatus
import com.mantrasiddhi.domain.repository.SandhyaLogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SandhyaLogRepositoryImpl @Inject constructor(
    private val dao: SandhyaLogDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SandhyaLogRepository {

    override fun observeLogs(userId: Long): Flow<List<SandhyaLog>> =
        dao.observeForUser(userId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getLogs(userId: Long): List<SandhyaLog> = withContext(ioDispatcher) {
        dao.getForUserSync(userId).map { it.toDomain() }
    }

    override suspend fun logNotified(
        userId: Long,
        period: SandhyaPeriod,
        scheduledEpochMillis: Long,
        dateKey: String
    ) = withContext(ioDispatcher) {
        dao.insertIgnore(
            SandhyaLogEntity(
                userId = userId,
                period = period.name,
                scheduledEpochMillis = scheduledEpochMillis,
                status = SandhyaStatus.NOTIFIED.name,
                observedEpochMillis = null,
                dateKey = dateKey
            )
        )
        Unit
    }

    override suspend fun markObserved(
        userId: Long,
        period: SandhyaPeriod,
        dateKey: String,
        observedEpochMillis: Long
    ) = withContext(ioDispatcher) {
        dao.markObserved(userId, dateKey, period.name, observedEpochMillis)
    }

    override suspend fun sweepMissed(userId: Long, todayKey: String) = withContext(ioDispatcher) {
        dao.sweepMissed(userId, todayKey)
    }
}
