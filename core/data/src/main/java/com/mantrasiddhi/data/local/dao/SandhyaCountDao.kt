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

    @Query("SELECT * FROM sandhya_count_changes WHERE userId = :userId AND dateKey = :dateKey AND period = :period AND (revision < :beforeRevision OR (revision = :beforeRevision AND id < :beforeId)) ORDER BY revision DESC, id DESC LIMIT 200")
    abstract suspend fun historyPage(userId: Long, dateKey: String, period: String,
        beforeRevision: Long = Long.MAX_VALUE, beforeId: String = "\uffff"): List<SandhyaCountChangeEntity>

    /** Compatibility list API; the selected-history UI adopts page demand in step 09. */
    @Transaction
    open suspend fun history(userId: Long, dateKey: String, period: String): List<SandhyaCountChangeEntity> {
        val result = mutableListOf<SandhyaCountChangeEntity>()
        var revision = Long.MAX_VALUE
        var id = "\uffff"
        do {
            val page = historyPage(userId, dateKey, period, revision, id)
            result.addAll(page)
            page.lastOrNull()?.let { revision = it.revision; id = it.id }
        } while (page.size == 200)
        return result
    }

    @Upsert
    protected abstract suspend fun putCount(count: SandhyaCountEntity)

    @Upsert
    protected abstract suspend fun putDraft(draft: SandhyaCountDraftEntity)

    @Insert
    protected abstract suspend fun insertChange(change: SandhyaCountChangeEntity)

    @Query("DELETE FROM sandhya_count_drafts WHERE userId = :userId AND id = :id")
    abstract suspend fun discardDraft(userId: Long, id: String)

    /** A remote revision invalidates an open editor instead of silently overwriting its base. */
    @Transaction
    open suspend fun rebuildPeriods(userId: Long, recordHistory: Boolean = true) {
        do {
            val keys = projectionIds(userId, "period")
            for (id in keys) {
                clearProjection(userId, "period", id)
                val key = id.substringBefore(':') to id.substringAfter(':')
                var time = Long.MAX_VALUE
                var beforeId = "\uffff"
                var sum = java.math.BigInteger.ZERO
                var oldestZone: String? = null
                do {
                    val page = periodPage(userId, key.first, key.second, time, beforeId)
                    for (entry in page) sum += java.math.BigInteger.valueOf(entry.quantity)
                    page.lastOrNull()?.let { time = it.createdAtMillis; beforeId = it.id; oldestZone = it.zoneId }
                } while (page.size == 200)
                if (oldestZone == null) continue
                val total = sum.longValueExact()
                val old = getCount(userId, key.first, key.second)
                if (old?.quantity == total) continue
                val revision = Math.addExact(old?.revision ?: 0, 1)
                val zone = old?.zoneId ?: oldestZone!!
                putCount(SandhyaCountEntity(userId, key.first, key.second, total, revision, zone))
                if (recordHistory) insertChange(SandhyaCountChangeEntity(java.util.UUID.randomUUID().toString(), userId,
                    key.first, key.second, zone, "EDIT", old?.quantity, total, revision, System.currentTimeMillis()))
            }
        } while (keys.size == 200)
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
        clearProjection(draft.userId, "period", "${draft.dateKey}:${draft.period.name}")
        discardDraft(draft.userId, draft.id)
        return change
    }
}
