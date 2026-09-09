package com.mantrasiddhi.data.local.dao

import androidx.room.*
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.util.SandhyaDateKey
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

@Dao
abstract class JapaDao {
    @Query("SELECT * FROM japa_entries WHERE userId = :userId ORDER BY createdAtMillis, id")
    abstract fun observeEntries(userId: Long): Flow<List<JapaEntryEntity>>

    @Query("SELECT * FROM japa_entries WHERE userId = :userId ORDER BY createdAtMillis, id")
    abstract suspend fun entries(userId: Long): List<JapaEntryEntity>

    @Query("SELECT * FROM japa_entries WHERE id = :id")
    abstract suspend fun entry(id: String): JapaEntryEntity?

    @Query("SELECT * FROM japa_entries WHERE userId = :userId AND sessionId = :sessionId LIMIT 1")
    abstract suspend fun audioEntry(userId: Long, sessionId: Long): JapaEntryEntity?

    @Upsert
    abstract suspend fun putEntry(entry: JapaEntryEntity)

    @Query("SELECT * FROM targets WHERE userId = :userId ORDER BY startedAtMillis DESC, stableId DESC")
    abstract suspend fun targets(userId: Long): List<TargetEntity>

    @Query("SELECT * FROM purashcharana WHERE userId = :userId ORDER BY startedAtMillis DESC, stableId DESC")
    abstract suspend fun vows(userId: Long): List<PurashcharanaEntity>

    @Update
    abstract suspend fun putTarget(target: TargetEntity)

    @Update
    abstract suspend fun putVow(vow: PurashcharanaEntity)

    /** Calendar dates stay in the timezone in which the practice was recorded. */
    private fun eligible(start: Long, end: Long?, dateKey: String, zone: String, recordedAt: Long): Boolean {
        val date = SandhyaDateKey.parse(dateKey)
        val timezone = ZoneId.of(zone)
        val dayEnd = date.plusDays(1).atStartOfDay(timezone).toInstant().toEpochMilli()
        return start <= recordedAt && start < dayEnd && (end == null || minOf(recordedAt, dayEnd - 1) < end)
    }

    private fun targetFor(entry: JapaEntryEntity, targets: List<TargetEntity>): TargetEntity? {
        val candidates = targets.filter {
            it.mantraId == entry.mantraId &&
                (entry.source == "AUDIO_ACCEPTED" || it.setByUserId == it.userId)
        }
        // Practice performed during a goal keeps its original allocation rules.
        candidates.firstOrNull {
            eligible(it.startedAtMillis, it.endedAtMillis, entry.dateKey, entry.zoneId, entry.createdAtMillis)
        }?.let { return it }
        // A self-set goal also includes unclaimed practice from earlier on its first day.
        // Use the entry's recorded timezone, never a device's current timezone.
        return candidates.asReversed().firstOrNull {
            it.setByUserId == it.userId && entry.createdAtMillis > 0 &&
                entry.createdAtMillis < it.startedAtMillis &&
                java.time.Instant.ofEpochMilli(it.startedAtMillis).atZone(ZoneId.of(entry.zoneId)).toLocalDate() ==
                    SandhyaDateKey.parse(entry.dateKey) &&
                java.time.Instant.ofEpochMilli(entry.createdAtMillis).atZone(ZoneId.of(entry.zoneId)).toLocalDate() ==
                    SandhyaDateKey.parse(entry.dateKey)
        }
    }

    /** Repair only unclaimed entries. Corrections and later goals never reuse claimed japa. */
    suspend fun creditUnassignedTargetEntries(userId: Long, now: Long): Boolean {
        val targets = targets(userId)
        var changed = false
        for (entry in entries(userId).filter { it.targetId == null && it.createdAtMillis > 0 }) {
            val target = targetFor(entry, targets) ?: continue
            putEntry(entry.copy(targetId = target.stableId, revision = Math.addExact(entry.revision, 1),
                updatedAtMillis = maxOf(entry.updatedAtMillis, now), operationId = java.util.UUID.randomUUID().toString()))
            changed = true
        }
        return changed
    }

    suspend fun allocate(entry: JapaEntryEntity): JapaEntryEntity {
        val target = targetFor(entry, targets(entry.userId))
        val vow = vows(entry.userId).firstOrNull {
            it.mantraId == entry.mantraId &&
                eligible(it.startedAtMillis, it.endedAtMillis, entry.dateKey, entry.zoneId, entry.createdAtMillis)
        }
        return entry.copy(targetId = target?.stableId, vowId = vow?.stableId)
    }

    /** Recompute from uncapped quantities, so an edit after an overshoot remains correct. */
    suspend fun project(userId: Long, now: Long): JapaGoalFeedback {
        creditUnassignedTargetEntries(userId, now)
        val entries = entries(userId)
        var feedback = JapaGoalFeedback()
        for (target in targets(userId)) {
            val count = JapaAccounting.credited(target.baselineCount,
                entries.filter { it.targetId == target.stableId }.map { it.quantity }, target.targetRecitationCount.toLong()).toInt()
            val complete = count >= target.targetRecitationCount
            feedback = feedback.copy(
                targetCompleted = feedback.targetCompleted || (!target.isCompleted && complete),
                completionReversed = feedback.completionReversed || (target.isCompleted && !complete))
            putTarget(target.copy(currentRecitationCount = count, isCompleted = complete))
        }
        for (vow in vows(userId)) {
            val count = JapaAccounting.credited(vow.baselineCount,
                entries.filter { it.vowId == vow.stableId }.map { it.quantity }, vow.targetJapaCount)
            val complete = count >= vow.targetJapaCount
            feedback = feedback.copy(
                purashcharanaCompleted = feedback.purashcharanaCompleted || (vow.completedAtMillis == null && complete),
                completionReversed = feedback.completionReversed || (vow.completedAtMillis != null && !complete),
                milestonesCrossed = feedback.milestonesCrossed + JapaAccounting.crossed(vow.currentJapaCount, count, vow.mantraAksharaCount).size)
            putVow(vow.copy(currentJapaCount = count,
                completedAtMillis = if (complete) vow.completedAtMillis ?: now else null))
        }
        return feedback
    }

    /** Every addition has an identity. Absolute corrections revise the original allocations. */
    suspend fun recordSandhya(change: SandhyaCountChangeEntity): JapaGoalFeedback {
        val old = entries(change.userId).filter { it.dateKey == change.dateKey && it.period == change.period }
        val delta = change.quantity - (change.previousQuantity ?: 0)
        if (delta > 0 || old.isEmpty()) {
            val fresh = JapaEntryEntity(change.id, change.userId, "gayatri", change.dateKey, change.zoneId,
                change.period, delta.coerceAtLeast(0), 1, change.createdAtMillis, null, null, "SANDHYA")
            // Raising an old total is a correction of the last entry, not enrollment in today's goal.
            val previous = old.lastOrNull()
            if (change.mode == "EDIT" && previous != null) {
                putEntry(previous.copy(quantity = Math.addExact(previous.quantity, delta), revision = previous.revision + 1, updatedAtMillis = change.createdAtMillis, operationId = change.id))
            } else putEntry(allocate(fresh))
        } else if (delta < 0) {
            var remaining = -delta
            for (entry in old.asReversed()) {
                val removed = minOf(remaining, entry.quantity)
                if (removed > 0) putEntry(entry.copy(quantity = entry.quantity - removed, revision = entry.revision + 1, updatedAtMillis = change.createdAtMillis, operationId = change.id))
                remaining -= removed
                if (remaining == 0L) break
            }
            check(remaining == 0L) { "The count history needs to be restored before this correction" }
        }
        return project(change.userId, change.createdAtMillis)
    }

    @Transaction
    open suspend fun recordAudio(sessionId: Long, session: RecitationSessionEntity) {
        if (!session.isCorrect || session.mode != "NORMAL") return
        if (audioEntry(session.userId, sessionId) != null) return
        val id = java.util.UUID.randomUUID().toString()
        val zone = ZoneId.systemDefault()
        val date = java.time.Instant.ofEpochMilli(session.createdAt).atZone(zone).toLocalDate()
        putEntry(allocate(JapaEntryEntity(id, session.userId, session.mantraId, SandhyaDateKey.of(date), zone.id,
            null, 1, 1, session.createdAt, null, null, "AUDIO_ACCEPTED", sessionId = sessionId)))
        project(session.userId, session.createdAt)
    }
}
