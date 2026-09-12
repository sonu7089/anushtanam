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

    @Query("SELECT * FROM japa_entries WHERE userId = :userId AND dateKey = :date AND period = :period")
    abstract suspend fun periodEntries(userId: Long, date: String, period: String): List<JapaEntryEntity>

    @Query("SELECT * FROM japa_entries WHERE id = :id")
    abstract suspend fun entry(id: String): JapaEntryEntity?

    @Query("SELECT * FROM japa_entries WHERE userId = :userId AND sessionId = :sessionId LIMIT 1")
    abstract suspend fun audioEntry(userId: Long, sessionId: Long): JapaEntryEntity?

    @Query("SELECT * FROM japa_entries WHERE userId = :userId AND sessionStableId = :stableId LIMIT 1")
    abstract suspend fun audioEntryByStableId(userId: Long, stableId: String): JapaEntryEntity?

    @Query("SELECT * FROM recitation_sessions WHERE id = :id")
    protected abstract suspend fun session(id: Long): RecitationSessionEntity?

    @Query("SELECT * FROM recitation_sessions WHERE userId = :userId AND (stableId = :stableId OR acceptedEntryId = :entryId) ORDER BY id LIMIT 1")
    protected abstract suspend fun referencedSession(userId: Long, stableId: String?, entryId: String): RecitationSessionEntity?

    @Query("UPDATE recitation_sessions SET acceptedEntryId = :entryId WHERE id = :sessionId AND acceptedEntryId IS NULL")
    protected abstract suspend fun linkSessionEntry(sessionId: Long, entryId: String)

    @Query("SELECT COUNT(*) FROM identity_repair_archive WHERE tableName = 'japa_entries' AND rowId = :id")
    protected abstract suspend fun archivedEntry(id: String): Int

    @Upsert
    protected abstract suspend fun writeEntry(entry: JapaEntryEntity)

    /** Missing cloud references are eventual; known references must agree before admission. */
    suspend fun entryRejection(value: JapaEntryEntity): String? {
        val old = entry(value.id)
        if (old == null && archivedEntry(value.id) != 0) return "Legacy entry is retained in the identity repair archive"
        if (old != null) {
            if (old.userId != value.userId) return "Entry identity belongs to another local profile"
            if (old.mantraId != value.mantraId || old.source != value.source || old.period != value.period ||
                old.dateKey != value.dateKey || old.zoneId != value.zoneId || old.createdAtMillis != value.createdAtMillis ||
                (old.targetId != null && old.targetId != value.targetId) || (old.vowId != null && old.vowId != value.vowId) ||
                (old.sessionId != null && old.sessionId != value.sessionId) ||
                (old.sessionStableId != null && old.sessionStableId != value.sessionStableId)) return "Original entry identity/allocation is immutable"
        }
        if (value.source == "AUDIO_ACCEPTED") {
            if (value.quantity != 1L || value.period != null) return "Accepted audio contributes exactly one japa"
        } else if (value.source != "SANDHYA" || value.period == null || value.sessionId != null || value.sessionStableId != null || value.quantity < 0) {
            return "Invalid manual entry association"
        }
        val target = targets(value.userId).firstOrNull { it.stableId == value.targetId }
        if (target != null && (target.mantraId != value.mantraId ||
                (value.source != "AUDIO_ACCEPTED" && target.setByUserId != target.userId))) return "Target ownership/mantra does not permit this entry"
        val vow = vows(value.userId).firstOrNull { it.stableId == value.vowId }
        if (vow != null && vow.mantraId != value.mantraId) return "Vow mantra does not match entry"
        val session = if (value.sessionId != null) session(value.sessionId) ?: return "Unknown local session reference"
            else referencedSession(value.userId, value.sessionStableId, value.id)
        if (session != null) {
            sessionRejection(value, session)?.let { return it }
            if (audioEntry(value.userId, session.id)?.id?.let { it != value.id } == true) return "Session already has an accepted entry"
        }
        if (value.sessionStableId != null && audioEntryByStableId(value.userId, value.sessionStableId)?.id?.let { it != value.id } == true) {
            return "Session identity already has an accepted entry"
        }
        return null
    }

    fun sessionRejection(entry: JapaEntryEntity, session: RecitationSessionEntity): String? =
        if (entry.userId != session.userId || entry.mantraId != session.mantraId || entry.source != "AUDIO_ACCEPTED" ||
            entry.quantity != 1L || entry.period != null || !session.isCorrect || session.mode != "NORMAL" ||
            entry.createdAtMillis != session.createdAt || (entry.sessionId != null && entry.sessionId != session.id) ||
            (entry.sessionStableId != null && entry.sessionStableId != session.stableId) ||
            (session.acceptedEntryId != null && session.acceptedEntryId != entry.id)) "Session and accepted entry disagree" else null

    @Transaction
    open suspend fun putEntry(entry: JapaEntryEntity) {
        val session = entry.sessionId?.let { session(it) } ?: referencedSession(entry.userId, entry.sessionStableId, entry.id)
        val linked = if (session == null) entry else entry.copy(sessionId = entry.sessionId ?: session.id, sessionStableId = entry.sessionStableId ?: session.stableId)
        val reason = entryRejection(linked)
        require(reason == null) { reason.orEmpty() }
        writeEntry(linked)
        if (session != null) linkSessionEntry(session.id, linked.id)
    }

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
            it.setByUserId == it.userId && (it.endedAtMillis == null || it.endedAtMillis > it.startedAtMillis) && entry.createdAtMillis > 0 &&
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
        val saved = this.session(sessionId)
        require(saved != null && saved.copy(acceptedEntryId = null) == session.copy(id = sessionId, acceptedEntryId = null)) { "Audio credit requires the persisted immutable assessment" }
        if (!session.isCorrect || session.mode != "NORMAL") return
        if (audioEntry(session.userId, sessionId) != null) return
        val id = java.util.UUID.randomUUID().toString()
        val zone = ZoneId.systemDefault()
        val date = java.time.Instant.ofEpochMilli(session.createdAt).atZone(zone).toLocalDate()
        putEntry(allocate(JapaEntryEntity(id, session.userId, session.mantraId, SandhyaDateKey.of(date), zone.id,
            null, 1, 1, session.createdAt, null, null, "AUDIO_ACCEPTED", sessionId = sessionId, sessionStableId = session.stableId)))
        project(session.userId, session.createdAt)
    }
}
