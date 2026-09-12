/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import androidx.room.withTransaction
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
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
    private val database: MantraSiddhiDatabase,
    private val sync: com.mantrasiddhi.data.sync.JapaCloudSync,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RecitationRepository {

    override suspend fun saveSession(session: RecitationSession): Long = withContext(ioDispatcher) {
        val result = database.withTransaction {
            val owner = requireNotNull(database.userDao().getById(session.userId)) { "Unknown session owner" }
            require(session.stableId.isNotBlank()) { "Session identity is required" }
            val old = if (session.id > 0) requireNotNull(database.backupDao().sessionByLocalId(session.id)) {
                "Unknown saved session"
            } else database.backupDao().session(session.userId, session.stableId)
            val zone = java.time.ZoneId.systemDefault()
            val row = RecitationSessionEntity.fromDomain(session).copy(
                projectionDate = java.time.Instant.ofEpochMilli(session.createdAt).atZone(zone).toLocalDate().toString(),
                projectionZoneId = zone.id,
                childGoalEligible = owner.profileType == "CHILD" && session.isCorrect &&
                    session.mode == com.mantrasiddhi.domain.model.RecitationMode.NORMAL
            )
            if (old != null) {
                require(row.copy(id = old.id, acceptedEntryId = old.acceptedEntryId,
                    projectionDate = old.projectionDate, projectionZoneId = old.projectionZoneId,
                    childGoalEligible = old.childGoalEligible) == old) { "Saved assessments are immutable" }
                return@withTransaction old.id
            }

            val id = recitationDao.insert(row)
            database.japaDao().recordAudio(id, row)
            sync.enqueue(session.userId)
            id
        }
        sync.schedule()
        result
    }

    override fun getSessionsForUser(userId: Long): Flow<List<RecitationSession>> {
        return com.mantrasiddhi.domain.diagnostics.WorkProbe.observe("sessions.full",
            recitationDao.getSessionsForUser(userId)
                .map { list ->
                    com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("sessions.materializedRows", list.size.toLong())
                    list.map { it.toDomain() }
                }
                .flowOn(ioDispatcher))
    }

    override fun getRecentSessionsForUser(userId: Long, limit: Int): Flow<List<RecitationSession>> =
        recitationDao.getRecentSessionsForUser(userId, limit)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getStats(userId: Long): RecitationStats = withContext(ioDispatcher) {
        recitationDao.getStats(userId)
    }
}
