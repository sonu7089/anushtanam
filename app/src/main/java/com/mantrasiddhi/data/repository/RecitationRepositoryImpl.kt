/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import com.mantrasiddhi.data.local.dao.RecitationDao
import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.model.RecitationStats
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RecitationRepositoryImpl @Inject constructor(
    private val recitationDao: RecitationDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RecitationRepository {

    override suspend fun saveSession(session: RecitationSession): Long = withContext(ioDispatcher) {
        recitationDao.insert(RecitationSessionEntity.fromDomain(session))
    }

    override fun getSessionsForUser(userId: Long): Flow<List<RecitationSession>> {
        return recitationDao.getSessionsForUser(userId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getStats(userId: Long): RecitationStats = withContext(ioDispatcher) {
        val list = recitationDao.getSessionsForUserSync(userId)
        if (list.isEmpty()) {
            RecitationStats(
                totalSessions = 0,
                averageScore = 0.0,
                correctSessions = 0,
                totalDurationMs = 0L
            )
        } else {
            val total = list.size
            val avg = list.map { it.overallScore }.average()
            val correct = list.count { it.isCorrect }
            val duration = list.sumOf { it.durationMs }
            RecitationStats(
                totalSessions = total,
                averageScore = avg,
                correctSessions = correct,
                totalDurationMs = duration
            )
        }
    }
}
