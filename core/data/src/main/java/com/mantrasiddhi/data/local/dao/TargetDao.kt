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
import androidx.room.Update
import com.mantrasiddhi.data.local.entity.TargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(target: TargetEntity): Long

    @Query("SELECT * FROM targets WHERE userId = :userId AND stableId = :stableId LIMIT 1")
    suspend fun getByStableId(userId: Long, stableId: String): TargetEntity?

    @Query("SELECT * FROM targets WHERE id = :id")
    suspend fun getById(id: Long): TargetEntity?

    @Update
    suspend fun update(target: TargetEntity)

    @Query("SELECT * FROM targets WHERE userId = :userId AND isCompleted = 0 AND isActive = 1 ORDER BY startedAtMillis DESC, stableId DESC LIMIT 1")
    suspend fun getActiveForUser(userId: Long): TargetEntity?

    @Query("SELECT * FROM targets WHERE userId = :userId")
    fun getAllForUser(userId: Long): Flow<List<TargetEntity>>

    @Query("UPDATE targets SET isActive = 0, endedAtMillis = :now WHERE id = :id")
    suspend fun deleteById(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE targets SET isActive = 0, endedAtMillis = :now WHERE userId = :userId AND isActive = 1")
    suspend fun closePrevious(userId: Long, now: Long)
}
