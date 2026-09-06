package com.mantrasiddhi.data.local.dao

import androidx.room.*
import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import com.mantrasiddhi.data.local.entity.CloudSyncScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CloudSyncDao {
    @Insert abstract suspend fun insert(event: CloudSyncEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setSchedule(schedule: CloudSyncScheduleEntity)
    @Query("SELECT * FROM cloud_sync_outbox WHERE uid = :uid ORDER BY id LIMIT :limit")
    abstract suspend fun pending(uid: String, limit: Int = 100): List<CloudSyncEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM cloud_sync_outbox WHERE uid = :uid)")
    abstract suspend fun hasPending(uid: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM cloud_sync_outbox WHERE uid = :uid AND kind IN (:kinds))")
    abstract suspend fun hasKinds(uid: String, kinds: List<String>): Boolean
    @Query("SELECT DISTINCT uid FROM cloud_sync_outbox")
    abstract fun pendingAccounts(): Flow<List<String>>
    @Query("SELECT * FROM cloud_sync_outbox WHERE uid = :uid AND kind = :kind ORDER BY id")
    abstract fun observePending(uid: String, kind: String): Flow<List<CloudSyncEntity>>
    @Query("DELETE FROM cloud_sync_outbox WHERE id = :id")
    abstract suspend fun acknowledge(id: Long)
    @Query("DELETE FROM cloud_sync_outbox WHERE id IN (:ids)")
    abstract suspend fun acknowledgeBatch(ids: List<Long>)
    @Query("UPDATE cloud_sync_schedule SET nextAttemptAtMs = :nextMs WHERE uid = :uid AND nextAttemptAtMs <= :nowMs")
    abstract suspend fun claim(uid: String, nowMs: Long, nextMs: Long): Int
    @Query("SELECT nextAttemptAtMs FROM cloud_sync_schedule WHERE uid = :uid")
    abstract suspend fun nextAttemptAt(uid: String): Long?

    @Transaction
    open suspend fun enqueue(event: CloudSyncEntity): Long {
        // Further recitations must not reset the timer and postpone sync indefinitely.
        if (!hasPending(event.uid)) setSchedule(CloudSyncScheduleEntity(event.uid, event.createdAtMs + 60_000L))
        return insert(event)
    }
}
