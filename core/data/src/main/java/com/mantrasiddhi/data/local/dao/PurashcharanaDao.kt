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
import com.mantrasiddhi.data.local.entity.PurashcharanaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurashcharanaDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(purashcharana: PurashcharanaEntity): Long

    /**
     * Replaces a whole row. Used only by the cloud restore, which has to seed fields
     * (count, start instant, completion stamp) that no ordinary flow may set — the japa
     * path still goes exclusively through [incrementJapa].
     */
    @Update
    suspend fun update(purashcharana: PurashcharanaEntity)

    @Query("SELECT * FROM purashcharana WHERE userId = :userId AND isActive = 1 LIMIT 1")
    fun observeActiveForUser(userId: Long): Flow<PurashcharanaEntity?>

    @Query("SELECT * FROM purashcharana WHERE userId = :userId AND isActive = 1 LIMIT 1")
    suspend fun getActiveForUser(userId: Long): PurashcharanaEntity?

    /**
     * Adds one japa in a single statement.
     *
     * Recitations are evaluated off a queue and several can settle in quick succession,
     * so a read-modify-write from Kotlin could lose a count. SQLite applies this UPDATE
     * atomically, which makes that impossible. The `currentJapaCount < targetJapaCount`
     * guard also caps the count at the sankalpa target — a fulfilled Purashcharana
     * cannot drift past 100%.
     *
     * @return the number of rows changed: 0 when there is no active sankalpa, or when
     *   it is already fulfilled.
     */
    @Query(
        "UPDATE purashcharana SET currentJapaCount = currentJapaCount + 1 " +
            "WHERE userId = :userId AND isActive = 1 AND currentJapaCount < targetJapaCount"
    )
    suspend fun incrementJapa(userId: Long): Int

    /**
     * Stamps the Purnahuti moment once the target is reached. Written as a conditional
     * UPDATE so the stamp records the first crossing and is never overwritten.
     */
    @Query(
        "UPDATE purashcharana SET completedAtMillis = :completedAtMillis " +
            "WHERE userId = :userId AND isActive = 1 AND completedAtMillis IS NULL " +
            "AND currentJapaCount >= targetJapaCount"
    )
    suspend fun markCompleted(userId: Long, completedAtMillis: Long)

    /** Withdrawal: deactivate rather than delete, so the japa already done is not erased. */
    @Query("UPDATE purashcharana SET isActive = 0 WHERE userId = :userId AND isActive = 1")
    suspend fun deactivateForUser(userId: Long)
}
