/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import androidx.room.withTransaction

import com.mantrasiddhi.data.local.dao.UserDao
import com.mantrasiddhi.data.local.entity.UserProfileEntity
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val accountRecords: com.mantrasiddhi.data.sync.AccountRecordStore? = null,
    private val database: com.mantrasiddhi.data.local.MantraSiddhiDatabase? = null
) : UserRepository {

    override fun getAll(): Flow<List<UserProfile>> {
        return userDao.getAll()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getById(id: Long): UserProfile? = withContext(ioDispatcher) {
        userDao.getById(id)?.toDomain()
    }

    override fun getChildrenOf(parentId: Long): Flow<List<UserProfile>> {
        return userDao.getChildrenOf(parentId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getByFirebaseUid(uid: String): UserProfile? =
        userDao.getByFirebaseUid(uid)?.toDomain()

    override suspend fun hasPractice(id: Long): Boolean = userDao.hasPractice(id)

    override suspend fun insert(user: UserProfile): Long = withContext(ioDispatcher) {
        suspend fun save(): Long {
            val old = if (user.id > 0) userDao.getById(user.id) else null
            val id = userDao.insert(UserProfileEntity.fromDomain(user))
            if (old != null && old.firebaseUid == user.firebaseUid &&
                (old.name != user.name || old.avatarIndex != user.avatarIndex || old.useGooglePhoto != user.useGooglePhoto)) {
                accountRecords?.put(user.firebaseUid ?: "local:${user.id}", "profile", mapOf(
                    "name" to user.name, "avatarIndex" to user.avatarIndex, "useGooglePhoto" to user.useGooglePhoto))
            }
            return id
        }
        if (database != null) database.withTransaction { save() } else save()
    }
}
