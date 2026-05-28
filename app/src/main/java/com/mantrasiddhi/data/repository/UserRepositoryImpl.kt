/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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

    override suspend fun insert(user: UserProfile): Long = withContext(ioDispatcher) {
        userDao.insert(UserProfileEntity.fromDomain(user))
    }
}
