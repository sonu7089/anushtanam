package com.mantrasiddhi.data.local.dao

import androidx.room.*
import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import com.mantrasiddhi.data.local.entity.CloudSyncScheduleEntity
import com.mantrasiddhi.data.local.entity.BackupQueueState
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CloudSyncDao {
    @Query("SELECT COUNT(*) FROM cloud_sync_outbox WHERE uid = :uid")
    abstract fun observeCount(uid: String): Flow<Int>
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
    @Query("SELECT nextAttemptAtMs FROM cloud_sync_schedule WHERE uid = :uid")
    abstract suspend fun nextAttemptAt(uid: String): Long?

    @Query("SELECT CAST(payload AS INTEGER) FROM backup_queue_state WHERE uid = :uid AND `key` = 'sync:lastAttempt'")
    abstract suspend fun lastAttemptAt(uid: String): Long?
    @Upsert protected abstract suspend fun putAttempt(state: BackupQueueState)

    @Query("SELECT payload FROM backup_queue_state WHERE uid = :uid AND `key` = 'sync:timing'")
    abstract suspend fun retryTiming(uid: String): String?
    @Query("SELECT payload FROM backup_queue_state WHERE uid = :uid AND `key` = 'sync:timing'")
    abstract fun observeRetryTiming(uid: String): Flow<String?>
    @Upsert abstract suspend fun putRetryTiming(state: BackupQueueState)

    @Transaction
    open suspend fun saveRetryTiming(uid: String, now: Long, due: Long, payload: String) {
        setSchedule(CloudSyncScheduleEntity(uid, due))
        putAttempt(BackupQueueState(uid, "sync:lastAttempt", now.toString()))
        putRetryTiming(BackupQueueState(uid, "sync:timing", payload))
    }

    @Transaction
    open suspend fun enqueue(event: CloudSyncEntity): Long {
        // Further recitations must not reset the timer and postpone sync indefinitely.
        if (!hasPending(event.uid)) setSchedule(CloudSyncScheduleEntity(event.uid, event.createdAtMs + 60_000L))
        return insert(event)
    }
}
