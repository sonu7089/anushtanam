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
    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(isCorrect), 0) AS correct, COALESCE(AVG(overallScore), 0.0) AS average, COALESCE(MAX(overallScore), 0.0) AS best FROM recitation_sessions WHERE userId = :userId")
    fun observeTotals(userId: Long): Flow<com.mantrasiddhi.domain.model.SessionTotals>

    @Query("SELECT date(createdAt / 1000, 'unixepoch', 'localtime') AS dateKey, COUNT(*) AS count FROM recitation_sessions WHERE userId = :userId AND isCorrect = 1 GROUP BY dateKey ORDER BY dateKey DESC")
    fun observePracticeDays(userId: Long): Flow<List<com.mantrasiddhi.domain.model.PracticeDay>>

    @Query("SELECT date(createdAt / 1000, 'unixepoch', 'localtime') AS dateKey, AVG(overallScore) AS average, AVG(correctness) AS correctness, AVG(completeness) AS completeness, AVG(pronunciation) AS pronunciation FROM recitation_sessions WHERE userId = :userId AND createdAt >= :since AND createdAt < :until GROUP BY dateKey")
    fun observeScoreDays(userId: Long, since: Long, until: Long): Flow<List<com.mantrasiddhi.domain.model.ScoreDay>>

    @Query("SELECT id, createdAt, mode, overallScore, isCorrect, durationMs FROM recitation_sessions WHERE userId = :userId AND (createdAt < :beforeTime OR (createdAt = :beforeTime AND id < :beforeId)) ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeHistoryPage(userId: Long, beforeTime: Long, beforeId: Long, limit: Int): Flow<List<com.mantrasiddhi.domain.model.SessionHistoryRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
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
