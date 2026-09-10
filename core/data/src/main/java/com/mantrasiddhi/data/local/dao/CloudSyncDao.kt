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
    @Query("UPDATE cloud_sync_schedule SET nextAttemptAtMs = :nextMs WHERE uid = :uid AND nextAttemptAtMs <= :nowMs")
    abstract suspend fun claim(uid: String, nowMs: Long, nextMs: Long): Int
    @Query("SELECT nextAttemptAtMs FROM cloud_sync_schedule WHERE uid = :uid")
    abstract suspend fun nextAttemptAt(uid: String): Long?

    @Query("SELECT CAST(payload AS INTEGER) FROM backup_queue_state WHERE uid = :uid AND `key` = 'sync:lastAttempt'")
    abstract suspend fun lastAttemptAt(uid: String): Long?
    @Query("SELECT CAST(payload AS INTEGER) FROM backup_queue_state WHERE uid = :uid AND `key` = 'sync:lastAttempt'")
    abstract fun observeLastAttemptAt(uid: String): Flow<Long?>
    @Upsert protected abstract suspend fun putAttempt(state: BackupQueueState)

    /** Manual requests may skip the initial delay, but never the shared retry limit. */
    @Transaction
    open suspend fun claimSyncAttempt(uid: String, nowMs: Long, manual: Boolean): Boolean {
        val last = lastAttemptAt(uid)
        if (last != null && nowMs - last < 60_000L) return false
        if (!manual && (nextAttemptAt(uid) ?: 0L) > nowMs) return false
        setSchedule(CloudSyncScheduleEntity(uid, nowMs + 60_000L))
        putAttempt(BackupQueueState(uid, "sync:lastAttempt", nowMs.toString()))
        return true
    }

    @Transaction
    open suspend fun enqueue(event: CloudSyncEntity): Long {
        // Further recitations must not reset the timer and postpone sync indefinitely.
        if (!hasPending(event.uid)) setSchedule(CloudSyncScheduleEntity(event.uid, event.createdAtMs + 60_000L))
        return insert(event)
    }
}
