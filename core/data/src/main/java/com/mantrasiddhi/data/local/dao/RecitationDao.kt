/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import com.mantrasiddhi.domain.model.RecitationStats
import kotlinx.coroutines.flow.Flow

data class CorrectDayCount(val dateKey: String, val count: Int)

@Dao
interface RecitationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: RecitationSessionEntity): Long

    @Query("SELECT * FROM recitation_sessions WHERE userId = :userId ORDER BY id DESC")
    fun getSessionsForUser(userId: Long): Flow<List<RecitationSessionEntity>>

    @Query("SELECT * FROM recitation_sessions WHERE userId = :userId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun getRecentSessionsForUser(userId: Long, limit: Int): Flow<List<RecitationSessionEntity>>

    @Query("SELECT * FROM recitation_sessions WHERE userId = :userId")
    suspend fun getSessionsForUserSync(userId: Long): List<RecitationSessionEntity>

    @Query("SELECT COUNT(*) AS totalSessions, COALESCE(AVG(overallScore), 0.0) AS averageScore, " +
        "COALESCE(SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END), 0) AS correctSessions, " +
        "COALESCE(SUM(durationMs), 0) AS totalDurationMs FROM recitation_sessions WHERE userId = :userId")
    suspend fun getStats(userId: Long): RecitationStats

    @Query("SELECT COALESCE(MAX(overallScore), 0.0) FROM recitation_sessions WHERE userId = :userId")
    suspend fun getBestScore(userId: Long): Double

    @Query("SELECT date(createdAt / 1000, 'unixepoch', 'localtime') AS dateKey, COUNT(*) AS count " +
        "FROM recitation_sessions WHERE userId = :userId AND isCorrect = 1 AND createdAt >= :sinceMs " +
        "GROUP BY dateKey")
    suspend fun getCorrectDayCounts(userId: Long, sinceMs: Long): List<CorrectDayCount>
}
