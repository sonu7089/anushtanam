/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import com.mantrasiddhi.data.local.dao.PurashcharanaDao
import com.mantrasiddhi.data.local.entity.PurashcharanaEntity
import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.domain.model.PurashcharanaPath
import com.mantrasiddhi.domain.repository.PurashcharanaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurashcharanaRepositoryImpl @Inject constructor(
    private val purashcharanaDao: PurashcharanaDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PurashcharanaRepository {

    override fun observeActive(userId: Long): Flow<Purashcharana?> {
        return purashcharanaDao.observeActiveForUser(userId)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)
    }

    override suspend fun getActive(userId: Long): Purashcharana? = withContext(ioDispatcher) {
        purashcharanaDao.getActiveForUser(userId)?.toDomain()
    }

    override suspend fun enroll(userId: Long, aksharaCount: Int): Result<Purashcharana> =
        withContext(ioDispatcher) {
            runCatching {
                require(aksharaCount > 0) { "A mantra must have at least one akshara" }
                check(purashcharanaDao.getActiveForUser(userId) == null) {
                    "A Purashcharana is already active for this sadhak"
                }
                val entity = PurashcharanaEntity(
                    userId = userId,
                    mantraAksharaCount = aksharaCount,
                    targetJapaCount = PurashcharanaPath.targetFor(aksharaCount),
                    currentJapaCount = 0L,
                    startedAtMillis = System.currentTimeMillis(),
                    completedAtMillis = null,
                    isActive = true
                )
                entity.copy(id = purashcharanaDao.insert(entity)).toDomain()
            }
        }

    override suspend fun incrementJapa(userId: Long): Result<Purashcharana?> =
        withContext(ioDispatcher) {
            runCatching {
                // Single atomic UPDATE (see PurashcharanaDao.incrementJapa): the queued
                // recitations that can settle back-to-back must never lose a count.
                val rowsChanged = purashcharanaDao.incrementJapa(userId)
                if (rowsChanged > 0) {
                    purashcharanaDao.markCompleted(userId, System.currentTimeMillis())
                }
                // Re-read for the returned value. Null here means no active sankalpa,
                // which is the documented no-op; an already-fulfilled one still returns.
                purashcharanaDao.getActiveForUser(userId)?.toDomain()
            }
        }

    override suspend fun withdraw(userId: Long): Result<Unit> = withContext(ioDispatcher) {
        runCatching { purashcharanaDao.deactivateForUser(userId) }
    }

    override suspend fun adoptRemote(userId: Long, sankalpa: Purashcharana): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                // Re-key onto this device's profile and force the row active: the caller
                // has already decided this sankalpa is the one being carried forward.
                val restored = sankalpa.copy(userId = userId, isActive = true)
                val existing = purashcharanaDao.getActiveForUser(userId)
                if (existing == null) {
                    purashcharanaDao.insert(PurashcharanaEntity.fromDomain(restored.copy(id = 0L)))
                } else {
                    // Update in place rather than insert, so the restore cannot produce a
                    // second active row for the same sadhak.
                    purashcharanaDao.update(
                        PurashcharanaEntity.fromDomain(restored.copy(id = existing.id))
                    )
                }
                Unit
            }
        }
}
