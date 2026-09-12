package com.mantrasiddhi.data.local.dao

import androidx.room.*
import com.mantrasiddhi.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class BackupReviewRow(val id: String, val firstPayload: String, val secondPayload: String,
    val dateKey: String?, val quantity: Long?, val operationId: String?)

@Dao
interface BackupDao {
    @Query("SELECT * FROM changed_records WHERE userId = :userId AND kind IN ('entry','target','vow','session','log','change') ORDER BY kind, recordId LIMIT 200")
    suspend fun changed(userId: Long): List<ChangedRecordEntity>
    @Delete suspend fun clearChanged(record: ChangedRecordEntity)
    @Query("SELECT EXISTS(SELECT 1 FROM changed_records WHERE userId = :userId AND kind IN ('entry','target','vow','session','log','change'))")
    suspend fun hasChanges(userId: Long): Boolean
    @Query("SELECT DISTINCT u.firebaseUid FROM user_profiles u JOIN changed_records c ON u.id = c.userId WHERE u.firebaseUid IS NOT NULL AND c.kind IN ('entry','target','vow','session','log','change')")
    fun changedAccounts(): Flow<List<String>>
    @Query("INSERT OR IGNORE INTO changed_records SELECT userId, 'entry', id FROM japa_entries WHERE userId = :userId")
    suspend fun seedEntries(userId: Long)
    @Query("INSERT OR IGNORE INTO changed_records SELECT userId, 'target', CAST(id AS TEXT) FROM targets WHERE userId = :userId")
    suspend fun seedTargets(userId: Long)
    @Query("INSERT OR IGNORE INTO changed_records SELECT userId, 'vow', CAST(id AS TEXT) FROM purashcharana WHERE userId = :userId")
    suspend fun seedVows(userId: Long)
    @Query("INSERT OR IGNORE INTO changed_records SELECT userId, 'session', CAST(id AS TEXT) FROM recitation_sessions WHERE userId = :userId")
    suspend fun seedSessions(userId: Long)
    @Query("INSERT OR IGNORE INTO changed_records SELECT userId, 'log', CAST(id AS TEXT) FROM sandhya_logs WHERE userId = :userId")
    suspend fun seedLogs(userId: Long)
    @Query("INSERT OR IGNORE INTO changed_records SELECT userId, 'change', id FROM sandhya_count_changes WHERE userId = :userId")
    suspend fun seedChanges(userId: Long)
    @Query("SELECT * FROM targets WHERE userId = :userId AND id = :id")
    suspend fun targetById(userId: Long, id: Long): TargetEntity?
    @Query("SELECT * FROM purashcharana WHERE userId = :userId AND id = :id")
    suspend fun vowById(userId: Long, id: Long): PurashcharanaEntity?
    @Query("SELECT * FROM sandhya_logs WHERE userId = :userId AND id = :id")
    suspend fun logById(userId: Long, id: Long): SandhyaLogEntity?
    @Upsert suspend fun quarantine(record: BackupQuarantineEntity)
    @Query("DELETE FROM backup_quarantine WHERE uid = :uid AND source = :source")
    suspend fun clearQuarantine(uid: String, source: String)
    @Query("SELECT COUNT(*) FROM backup_quarantine WHERE uid = :uid")
    suspend fun quarantineCount(uid: String): Int
    @Query("SELECT COUNT(*) FROM backup_quarantine WHERE uid = :uid")
    fun observeQuarantineCount(uid: String): Flow<Int>
    @Query("SELECT * FROM backup_quarantine WHERE uid = :uid AND source > :after ORDER BY source LIMIT :limit")
    suspend fun quarantined(uid: String, after: String = "", limit: Int = 200): List<BackupQuarantineEntity>
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
    @Query("SELECT COUNT(*) FROM backup_conflicts WHERE uid = :uid AND resolved = 0")
    fun observeConflictCount(uid: String): Flow<Int>
    @Query("SELECT c.id, c.firstPayload, c.secondPayload, e.dateKey, e.quantity, e.operationId FROM backup_conflicts c LEFT JOIN japa_entries e ON e.id = c.entryId AND e.userId = :userId WHERE c.uid = :uid AND c.resolved = 0 ORDER BY c.id LIMIT 1")
    fun observeReview(uid: String, userId: Long): Flow<List<BackupReviewRow>>
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
    @Query("SELECT * FROM recitation_sessions WHERE userId = :userId AND acceptedEntryId = :entryId LIMIT 1")
    suspend fun sessionForEntry(userId: Long, entryId: String): RecitationSessionEntity?
    @Query("UPDATE recitation_sessions SET acceptedEntryId = :entryId WHERE id = :sessionId AND acceptedEntryId IS NULL")
    suspend fun linkSessionEntry(sessionId: Long, entryId: String)
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
