/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.Target
import kotlinx.coroutines.flow.Flow

interface TargetRepository {
    suspend fun create(target: Target): Long
    suspend fun update(target: Target)
    suspend fun getActiveForUser(userId: Long): Target?
    fun getAllForUser(userId: Long): Flow<List<Target>>
    suspend fun delete(targetId: Long)
}
