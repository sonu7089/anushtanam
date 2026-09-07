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
import kotlinx.coroutines.flow.map

interface RecitationRepository {
    suspend fun saveSession(session: RecitationSession): Long
    fun getSessionsForUser(userId: Long): Flow<List<RecitationSession>>
    /** Bounded history for the live recitation path; storage implementations limit in SQL. */
    fun getRecentSessionsForUser(userId: Long, limit: Int): Flow<List<RecitationSession>> =
        getSessionsForUser(userId).map { sessions ->
            sessions.sortedByDescending { it.createdAt }.take(limit)
        }
    suspend fun getStats(userId: Long): RecitationStats
}
