/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getAll(): Flow<List<UserProfile>>
    suspend fun getById(id: Long): UserProfile?
    fun getChildrenOf(parentId: Long): Flow<List<UserProfile>>
    suspend fun insert(user: UserProfile): Long
}
