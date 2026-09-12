/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mantrasiddhi.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user_profiles")
    fun getAll(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getById(id: Long): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE firebaseUid = :uid")
    suspend fun getByFirebaseUid(uid: String): UserProfileEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM recitation_sessions WHERE userId = :id) OR " +
        "EXISTS(SELECT 1 FROM targets WHERE userId = :id) OR " +
        "EXISTS(SELECT 1 FROM purashcharana WHERE userId = :id) OR " +
        "EXISTS(SELECT 1 FROM sandhya_logs WHERE userId = :id) OR " +
        "EXISTS(SELECT 1 FROM sandhya_counts WHERE userId = :id) OR " +
        "EXISTS(SELECT 1 FROM sandhya_count_drafts WHERE userId = :id)")
    suspend fun hasPractice(id: Long): Boolean

    @Query("SELECT * FROM user_profiles WHERE parentId = :parentId")
    fun getChildrenOf(parentId: Long): Flow<List<UserProfileEntity>>

    @Upsert
    suspend fun insert(user: UserProfileEntity): Long
}
