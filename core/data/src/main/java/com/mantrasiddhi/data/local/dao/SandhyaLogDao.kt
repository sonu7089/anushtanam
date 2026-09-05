/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mantrasiddhi.data.local.entity.SandhyaLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SandhyaLogDao {

    /**
     * Inserts a NOTIFIED row. [OnConflictStrategy.IGNORE] means a re-fired reminder
     * for an existing day+period is dropped rather than overwriting it, so an
     * already-OBSERVED occurrence is never reset.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(log: SandhyaLogEntity): Long

    @Query(
        "UPDATE sandhya_logs SET status = 'OBSERVED', observedEpochMillis = :observedAt " +
            "WHERE userId = :userId AND dateKey = :dateKey AND period = :period"
    )
    suspend fun markObserved(userId: Long, dateKey: String, period: String, observedAt: Long)

    @Query(
        "UPDATE sandhya_logs SET status = 'MISSED' " +
            "WHERE userId = :userId AND status = 'NOTIFIED' AND dateKey < :todayKey"
    )
    suspend fun sweepMissed(userId: Long, todayKey: String)

    @Query("SELECT * FROM sandhya_logs WHERE userId = :userId ORDER BY scheduledEpochMillis DESC")
    fun observeForUser(userId: Long): Flow<List<SandhyaLogEntity>>

    @Query("SELECT * FROM sandhya_logs WHERE userId = :userId")
    suspend fun getForUserSync(userId: Long): List<SandhyaLogEntity>
}
