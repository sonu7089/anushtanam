/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.model.RecitationStats
import kotlinx.coroutines.flow.Flow

interface RecitationRepository {
    suspend fun saveSession(session: RecitationSession): Long
    fun getSessionsForUser(userId: Long): Flow<List<RecitationSession>>
    suspend fun getStats(userId: Long): RecitationStats
}
