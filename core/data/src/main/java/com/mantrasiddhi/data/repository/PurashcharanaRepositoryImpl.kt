/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import androidx.room.withTransaction
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.sync.JapaCloudSync
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
    private val database: MantraSiddhiDatabase,
    private val sync: JapaCloudSync,
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

    override suspend fun enroll(userId: Long, mantraId: String, aksharaCount: Int): Result<Purashcharana> =
        withContext(ioDispatcher) {
            runCatching {
                require(mantraId.isNotBlank()) { "A sankalpa must name the mantra it is for" }
                require(aksharaCount > 0) { "A mantra must have at least one akshara" }
                check(purashcharanaDao.getActiveForUser(userId) == null) {
                    "A Purashcharana is already active for this sadhak"
                }
                val entity = PurashcharanaEntity(
                    userId = userId,
                    mantraId = mantraId,
                    mantraAksharaCount = aksharaCount,
                    targetJapaCount = PurashcharanaPath.targetFor(aksharaCount),
                    currentJapaCount = 0L,
                    startedAtMillis = System.currentTimeMillis(),
                    completedAtMillis = null,
                    isActive = true
                )
                val result = database.withTransaction {
                    require(database.userDao().getById(userId) != null) { "Unknown vow owner" }
                    check(purashcharanaDao.getActiveForUser(userId) == null)
                    val row = entity.copy(id = purashcharanaDao.insert(entity))
                    sync.enqueue(userId)
                    row.toDomain()
                }
                sync.schedule()
                result
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
        runCatching {
            database.withTransaction {
                purashcharanaDao.deactivateForUser(userId)
                sync.enqueue(userId)
            }
            sync.schedule()
        }
    }

    override suspend fun adoptRemote(userId: Long, sankalpa: Purashcharana): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                // Re-key onto this device's profile and force the row active: the caller
                // has already decided this sankalpa is the one being carried forward.
                require(database.userDao().getById(userId) != null) { "Unknown vow owner" }
                val restored = sankalpa.copy(userId = userId, isActive = true)
                val existing = purashcharanaDao.getActiveForUser(userId)
                if (existing != null && database.japaDao().vowEntries(userId, existing.stableId).isNotEmpty()) return@runCatching Unit
                if (existing == null) {
                    purashcharanaDao.insert(PurashcharanaEntity.fromDomain(restored.copy(id = 0L)))
                } else {
                    // Update in place rather than insert, so the restore cannot produce a
                    // second active row for the same sadhak.
                    val row = PurashcharanaEntity.fromDomain(restored.copy(id = existing.id))
                    if (row != existing) purashcharanaDao.update(row)
                }
                Unit
            }
        }
}
