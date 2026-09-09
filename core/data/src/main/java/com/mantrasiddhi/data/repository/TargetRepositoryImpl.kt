/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import androidx.room.withTransaction
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.dao.TargetDao
import com.mantrasiddhi.data.local.entity.TargetEntity
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.TargetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TargetRepositoryImpl @Inject constructor(
    private val targetDao: TargetDao,
    private val database: MantraSiddhiDatabase,
    private val sync: com.mantrasiddhi.data.sync.JapaCloudSync,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TargetRepository {

    override suspend fun create(target: Target): Long = withContext(ioDispatcher) {
        val result = database.withTransaction {
            targetDao.getByStableId(target.userId, target.stableId)?.let { return@withTransaction it.id }
            targetDao.closePrevious(target.userId, target.startedAtMillis)
            val id = targetDao.insert(TargetEntity.fromDomain(target))
            sync.enqueue(target.userId, database.japaDao().entries(target.userId))
            id
        }
        sync.schedule()
        result
    }

    override suspend fun update(target: Target) = withContext(ioDispatcher) {
        targetDao.update(TargetEntity.fromDomain(target))
    }

    override suspend fun getActiveForUser(userId: Long): Target? = withContext(ioDispatcher) {
        targetDao.getActiveForUser(userId)?.toDomain()
    }

    override fun getAllForUser(userId: Long): Flow<List<Target>> {
        return targetDao.getAllForUser(userId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun delete(targetId: Long) = withContext(ioDispatcher) {
        database.withTransaction {
            val target = targetDao.getById(targetId)?.userId
            targetDao.deleteById(targetId)
            if (target != null) sync.enqueue(target, database.japaDao().entries(target))
        }
        sync.schedule()
    }
}
