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
import com.mantrasiddhi.data.local.entity.SavingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(savings: SavingsEntity): Long

    @Update
    suspend fun update(savings: SavingsEntity)

    @Query("SELECT * FROM savings WHERE userId = :userId LIMIT 1")
    suspend fun getSavingsForUser(userId: Long): SavingsEntity?

    @Query("SELECT * FROM savings WHERE userId = :userId LIMIT 1")
    fun getSavingsFlowForUser(userId: Long): Flow<SavingsEntity?>
}
