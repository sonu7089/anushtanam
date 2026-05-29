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
import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecitationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: RecitationSessionEntity): Long

    @Query("SELECT * FROM recitation_sessions WHERE userId = :userId ORDER BY id DESC")
    fun getSessionsForUser(userId: Long): Flow<List<RecitationSessionEntity>>

    @Query("SELECT * FROM recitation_sessions WHERE userId = :userId")
    suspend fun getSessionsForUserSync(userId: Long): List<RecitationSessionEntity>
}
