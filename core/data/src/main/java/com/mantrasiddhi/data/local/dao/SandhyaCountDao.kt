package com.mantrasiddhi.data.local.dao

import androidx.room.*
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.domain.model.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SandhyaCountDao : JapaDao() {
    @Query("SELECT * FROM sandhya_counts WHERE userId = :userId ORDER BY dateKey DESC, period")
    abstract fun observeCounts(userId: Long): Flow<List<SandhyaCountEntity>>

    @Query("SELECT * FROM sandhya_counts WHERE userId = :userId AND dateKey = :dateKey AND period = :period")
    abstract suspend fun getCount(userId: Long, dateKey: String, period: String): SandhyaCountEntity?

    @Query("SELECT * FROM sandhya_count_drafts WHERE userId = :userId")
    abstract fun observeDraft(userId: Long): Flow<SandhyaCountDraftEntity?>

    @Query("SELECT * FROM sandhya_count_drafts WHERE userId = :userId")
    abstract suspend fun getDraft(userId: Long): SandhyaCountDraftEntity?

    @Query("SELECT * FROM sandhya_count_changes WHERE id = :id")
    abstract suspend fun getChange(id: String): SandhyaCountChangeEntity?

    @Query("SELECT * FROM sandhya_count_changes WHERE userId = :userId AND dateKey = :dateKey AND period = :period ORDER BY revision DESC")
    abstract suspend fun history(userId: Long, dateKey: String, period: String): List<SandhyaCountChangeEntity>

    @Upsert
    protected abstract suspend fun putCount(count: SandhyaCountEntity)

    @Upsert
    protected abstract suspend fun putDraft(draft: SandhyaCountDraftEntity)

    @Insert
    protected abstract suspend fun insertChange(change: SandhyaCountChangeEntity)

    @Query("DELETE FROM sandhya_count_drafts WHERE userId = :userId AND id = :id")
    abstract suspend fun discardDraft(userId: Long, id: String)

    /** A remote revision invalidates an open editor instead of silently overwriting its base. */
    suspend fun rebuildPeriods(userId: Long, recordHistory: Boolean = true) {
        for ((key, entries) in entries(userId).filter { it.period != null }.groupBy { it.dateKey to it.period!! }) {
            val total = entries.fold(java.math.BigInteger.ZERO) { sum, entry -> sum + java.math.BigInteger.valueOf(entry.quantity) }.longValueExact()
            val old = getCount(userId, key.first, key.second)
            if (old?.quantity == total) continue
            val revision = Math.addExact(old?.revision ?: 0, 1)
            val zone = old?.zoneId ?: entries.first().zoneId
            putCount(SandhyaCountEntity(userId, key.first, key.second, total, revision, zone))
            if (recordHistory) insertChange(SandhyaCountChangeEntity(java.util.UUID.randomUUID().toString(), userId,
                key.first, key.second, zone, "EDIT", old?.quantity, total, revision, System.currentTimeMillis()))
        }
    }

    @Transaction
    open suspend fun saveDraft(draft: SandhyaCountDraftEntity) {
        require(draft.userId > 0)
        // A delayed draft write must never resurrect an already completed session.
        if (getChange(draft.id) != null) return
        val current = getDraft(draft.userId)
        check(current == null || current.id == draft.id)
        putDraft(draft)
    }

    @Transaction
    open suspend fun save(draft: SandhyaCountDraft, now: Long): SandhyaCountChangeEntity {
        require(draft.userId > 0)
        getChange(draft.id)?.let {
            require(it.userId == draft.userId && it.dateKey == draft.dateKey && it.period == draft.period.name)
            return it
        }
        SandhyaCountMath.validateDate(draft.dateKey, draft.zoneId)
        val quantity = requireNotNull(draft.quantity)
        val previous = getCount(draft.userId, draft.dateKey, draft.period.name)
        if ((previous?.revision ?: 0) != draft.baseRevision) throw SandhyaCountConflict()
        val total = SandhyaCountMath.total(previous?.quantity, quantity, draft.mode)
        val revision = Math.addExact(previous?.revision ?: 0, 1)
        val zoneId = previous?.zoneId ?: draft.zoneId
        var change = SandhyaCountChangeEntity(draft.id, draft.userId, draft.dateKey, draft.period.name,
            zoneId, draft.mode.name, previous?.quantity, total, revision, now)
        val feedback = recordSandhya(change)
        change = change.copy(targetCompleted = feedback.targetCompleted, vowCompleted = feedback.purashcharanaCompleted,
            completionReversed = feedback.completionReversed, milestonesCrossed = feedback.milestonesCrossed)
        insertChange(change)
        putCount(SandhyaCountEntity(draft.userId, draft.dateKey, draft.period.name, total, revision, zoneId))
        discardDraft(draft.userId, draft.id)
        return change
    }
}
