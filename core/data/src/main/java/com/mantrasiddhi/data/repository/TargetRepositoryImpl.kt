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
import kotlinx.coroutines.flow.onStart
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
            require(database.userDao().getById(target.userId) != null) { "Unknown target owner" }
            targetDao.getByStableId(target.userId, target.stableId)?.let { return@withTransaction it.id }

            targetDao.closePrevious(target.userId, target.startedAtMillis)
            val id = targetDao.insert(TargetEntity.fromDomain(target))
            database.japaDao().project(target.userId, target.startedAtMillis, repair = false)
            sync.enqueue(target.userId)
            id
        }
        sync.schedule()
        result
    }

    override suspend fun update(target: Target) = withContext(ioDispatcher) {
        database.withTransaction {
            val old = requireNotNull(targetDao.getById(target.id)) { "Unknown target" }
            require(old.userId == target.userId && old.stableId == target.stableId && old.mantraId == target.mantraId && old.setByUserId == target.setByUserId) { "Target ownership and identity are immutable" }
            val row = TargetEntity.fromDomain(target)
            if (old != row) targetDao.update(row)
            database.japaDao().project(target.userId, System.currentTimeMillis(), repair = false)
            sync.enqueue(target.userId)
        }
    }

    override suspend fun getActiveForUser(userId: Long): Target? = withContext(ioDispatcher) {
        refreshGoalCredit(userId)
        targetDao.getActiveForUser(userId)?.toDomain()
    }

    override fun getAllForUser(userId: Long): Flow<List<Target>> {
        return targetDao.getAllForUser(userId)
            .onStart { refreshGoalCredit(userId) }
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    private suspend fun refreshGoalCredit(userId: Long) {
        database.withTransaction {
            database.japaDao().project(userId, System.currentTimeMillis(), repair = false)
            sync.enqueue(userId)
        }
    }

    override suspend fun delete(targetId: Long) = withContext(ioDispatcher) {
        database.withTransaction {
            val target = targetDao.getById(targetId)?.userId
            targetDao.deleteById(targetId)
            if (target != null) sync.enqueue(target)
        }
        sync.schedule()
    }
}
