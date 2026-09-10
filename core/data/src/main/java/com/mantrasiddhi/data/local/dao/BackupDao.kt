package com.mantrasiddhi.data.local.dao

import androidx.room.*
import com.mantrasiddhi.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {
    @Query("SELECT uid FROM backup_state WHERE restorePending = 1")
    fun pendingRestoreAccounts(): Flow<List<String>>
    @Query("SELECT * FROM backup_state WHERE uid = :uid")
    suspend fun state(uid: String): BackupStateEntity?
    @Query("SELECT * FROM backup_state WHERE uid = :uid")
    fun observeState(uid: String): Flow<BackupStateEntity?>
    @Upsert suspend fun putState(state: BackupStateEntity)
    @Query("SELECT payload FROM backup_queue_state WHERE uid = :uid AND `key` = :key")
    suspend fun queued(uid: String, key: String): String?
    @Upsert suspend fun putQueued(state: BackupQueueState)
    @Upsert suspend fun putConflict(conflict: BackupConflictEntity)
    @Query("SELECT * FROM backup_conflicts WHERE uid = :uid AND resolved = 0 ORDER BY id")
    fun observeConflicts(uid: String): Flow<List<BackupConflictEntity>>
    @Query("SELECT * FROM backup_conflicts WHERE uid = :uid AND id = :id")
    suspend fun conflict(uid: String, id: String): BackupConflictEntity?
    @Query("SELECT * FROM recitation_sessions WHERE userId = :userId AND id > :after ORDER BY id LIMIT 200")
    suspend fun sessions(userId: Long, after: Long): List<RecitationSessionEntity>
    @Query("SELECT * FROM recitation_sessions WHERE userId = :userId AND stableId = :id LIMIT 1")
    suspend fun session(userId: Long, id: String): RecitationSessionEntity?
    @Query("SELECT * FROM recitation_sessions WHERE id = :id")
    suspend fun sessionByLocalId(id: Long): RecitationSessionEntity?
    @Query("SELECT * FROM sandhya_logs WHERE userId = :userId AND id > :after ORDER BY id LIMIT 200")
    suspend fun logs(userId: Long, after: Long): List<SandhyaLogEntity>
    @Query("SELECT * FROM sandhya_logs WHERE userId = :userId AND dateKey = :date AND period = :period LIMIT 1")
    suspend fun log(userId: Long, date: String, period: String): SandhyaLogEntity?
    @Upsert suspend fun putLog(log: SandhyaLogEntity)
    @Query("SELECT * FROM sandhya_count_changes WHERE userId = :userId AND id > :after ORDER BY id LIMIT 200")
    suspend fun changes(userId: Long, after: String): List<SandhyaCountChangeEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putChange(change: SandhyaCountChangeEntity)
}
