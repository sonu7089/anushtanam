/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(target: TargetEntity): Long

    @Update
    suspend fun update(target: TargetEntity)

    @Query("SELECT * FROM targets WHERE userId = :userId AND isCompleted = 0 LIMIT 1")
    suspend fun getActiveForUser(userId: Long): TargetEntity?

    @Query("SELECT * FROM targets WHERE userId = :userId")
    fun getAllForUser(userId: Long): Flow<List<TargetEntity>>

    @Query("DELETE FROM targets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
