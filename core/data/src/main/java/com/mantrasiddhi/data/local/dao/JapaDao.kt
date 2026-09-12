package com.mantrasiddhi.data.local.dao

import androidx.room.*
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.util.SandhyaDateKey
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

data class PerformedJapaRow(val dateKey: String?, val createdAt: Long?, val quantity: Long)

@Dao
abstract class JapaDao {
    // Keep legacy unrepresented accepted audio, without reading word scores/transcription or summing Long quantities in SQLite.
    @Query("SELECT dateKey, NULL AS createdAt, quantity FROM japa_entries WHERE userId = :userId UNION ALL " +
        "SELECT NULL AS dateKey, r.createdAt, 1 AS quantity FROM recitation_sessions r WHERE r.userId = :userId AND r.isCorrect = 1 AND r.mode = 'NORMAL' " +
        "AND NOT EXISTS (SELECT 1 FROM japa_entries e WHERE e.userId = r.userId AND e.sessionId = r.id)")
    abstract fun observePerformed(userId: Long): Flow<List<PerformedJapaRow>>

    @Query("SELECT * FROM japa_entries WHERE userId = :userId ORDER BY createdAtMillis, id")
    abstract fun observeEntries(userId: Long): Flow<List<JapaEntryEntity>>

    @Query("SELECT * FROM japa_entries WHERE userId = :userId ORDER BY createdAtMillis, id")
    abstract suspend fun entries(userId: Long): List<JapaEntryEntity>

    @Query("SELECT * FROM japa_entries WHERE userId = :userId AND (createdAtMillis > :afterTime OR (createdAtMillis = :afterTime AND id > :afterId)) ORDER BY createdAtMillis, id LIMIT 200")
    abstract suspend fun historyPage(userId: Long, afterTime: Long = Long.MIN_VALUE, afterId: String = ""): List<JapaEntryEntity>

    @Query("SELECT * FROM japa_entries WHERE userId = :userId AND dateKey = :date AND period = :period ORDER BY createdAtMillis, id")
    abstract suspend fun periodEntries(userId: Long, date: String, period: String): List<JapaEntryEntity>

    @Query("SELECT * FROM japa_entries WHERE userId = :userId AND dateKey = :date AND period = :period AND (createdAtMillis < :beforeTime OR (createdAtMillis = :beforeTime AND id < :beforeId)) ORDER BY createdAtMillis DESC, id DESC LIMIT 200")
    abstract suspend fun periodPage(userId: Long, date: String, period: String,
        beforeTime: Long = Long.MAX_VALUE, beforeId: String = "\uffff"): List<JapaEntryEntity>

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

    @Query("SELECT * FROM targets WHERE userId = :userId AND stableId = :id")
    abstract suspend fun target(userId: Long, id: String): TargetEntity?
    @Query("SELECT * FROM purashcharana WHERE userId = :userId AND stableId = :id")
    abstract suspend fun vow(userId: Long, id: String): PurashcharanaEntity?
    @Query("SELECT * FROM japa_entries WHERE userId = :userId AND targetId = :id AND id > :after ORDER BY id LIMIT 200")
    abstract suspend fun targetEntries(userId: Long, id: String, after: String = ""): List<JapaEntryEntity>
    @Query("SELECT * FROM japa_entries WHERE userId = :userId AND vowId = :id AND id > :after ORDER BY id LIMIT 200")
    abstract suspend fun vowEntries(userId: Long, id: String, after: String = ""): List<JapaEntryEntity>
    @Query("SELECT * FROM japa_entries WHERE userId = :userId AND targetId IS NULL AND createdAtMillis > 0 AND id > :after ORDER BY id LIMIT 200")
    abstract suspend fun unassignedEntries(userId: Long, after: String): List<JapaEntryEntity>
    @Query("SELECT recordId FROM changed_records WHERE userId = :userId AND kind = :kind ORDER BY recordId LIMIT 200")
    abstract suspend fun projectionIds(userId: Long, kind: String): List<String>
    @Query("DELETE FROM changed_records WHERE userId = :userId AND kind = :kind AND recordId = :id")
    abstract suspend fun clearProjection(userId: Long, kind: String, id: String)
    @Query("INSERT OR IGNORE INTO changed_records SELECT userId, 'project_target', stableId FROM targets WHERE userId = :userId")
    protected abstract suspend fun seedTargetProjection(userId: Long)
    @Query("INSERT OR IGNORE INTO changed_records SELECT userId, 'project_vow', stableId FROM purashcharana WHERE userId = :userId")
    protected abstract suspend fun seedVowProjection(userId: Long)

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
        val target = value.targetId?.let { target(value.userId, it) }
        if (target != null && (target.mantraId != value.mantraId ||
                (value.source != "AUDIO_ACCEPTED" && target.setByUserId != target.userId))) return "Target ownership/mantra does not permit this entry"
        val vow = value.vowId?.let { vow(value.userId, it) }
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
        if (entry(linked.id) != linked) writeEntry(linked)
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
        if (targets.isEmpty()) return false
        var changed = false
        var after = ""
        do {
            val page = unassignedEntries(userId, after)
            for (entry in page) {
                val target = targetFor(entry, targets) ?: continue
                putEntry(entry.copy(targetId = target.stableId, revision = Math.addExact(entry.revision, 1),
                    updatedAtMillis = maxOf(entry.updatedAtMillis, now), operationId = java.util.UUID.randomUUID().toString()))
                changed = true
            }
            after = page.lastOrNull()?.id ?: after
        } while (page.size == 200)
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

    /** Returns whether any allocation changed, including entry-only remote arrivals. */
    @Transaction
    open suspend fun creditPendingTargetEntries(userId: Long, now: Long, force: Boolean = false): Boolean {
        var changed = false
        if (force || projectionIds(userId, "repair").isNotEmpty()) {
            changed = creditUnassignedTargetEntries(userId, now)
            clearProjection(userId, "repair", "targets")
        }
        var allocationTargets: List<TargetEntity>? = null
        do {
            val ids = projectionIds(userId, "allocate")
            for (id in ids) {
                clearProjection(userId, "allocate", id)
                val entry = entry(id)?.takeIf { it.userId == userId && it.targetId == null } ?: continue
                val candidates = allocationTargets ?: targets(userId).also { allocationTargets = it }
                val target = targetFor(entry, candidates) ?: continue
                changed = true
                putEntry(entry.copy(targetId = target.stableId, revision = Math.addExact(entry.revision, 1),
                    updatedAtMillis = maxOf(entry.updatedAtMillis, now), operationId = java.util.UUID.randomUUID().toString()))
            }
        } while (ids.size == 200)
        return changed
    }

    /** Recompute from uncapped quantities, so an edit after an overshoot remains correct. */
    @Transaction
    open suspend fun project(userId: Long, now: Long, repair: Boolean = true): JapaGoalFeedback {
        if (repair) {
            seedTargetProjection(userId)
            seedVowProjection(userId)
        }
        creditPendingTargetEntries(userId, now, force = repair)
        suspend fun count(baseline: Long, cap: Long, page: suspend (String) -> List<JapaEntryEntity>): Long {
            require(baseline >= 0 && cap >= 0)
            var total = baseline.coerceAtMost(cap)
            var after = ""
            do {
                val rows = page(after)
                for (row in rows) {
                    require(row.quantity >= 0)
                    total += minOf(row.quantity, cap - total)
                }
                after = rows.lastOrNull()?.id ?: after
            } while (rows.size == 200 && total < cap)
            return total
        }
        var feedback = JapaGoalFeedback()
        do {
            val ids = projectionIds(userId, "project_target")
            for (id in ids) {
                clearProjection(userId, "project_target", id)
                val target = target(userId, id) ?: continue
                val count = count(target.baselineCount, target.targetRecitationCount.toLong()) { targetEntries(userId, id, it) }.toInt()
                val complete = count >= target.targetRecitationCount
                feedback = feedback.copy(
                    targetCompleted = feedback.targetCompleted || (!target.isCompleted && complete),
                    completionReversed = feedback.completionReversed || (target.isCompleted && !complete))
                if (target.currentRecitationCount != count || target.isCompleted != complete)
                    putTarget(target.copy(currentRecitationCount = count, isCompleted = complete))
            }
        } while (ids.size == 200)
        do {
            val ids = projectionIds(userId, "project_vow")
            for (id in ids) {
                val vow = vow(userId, id)
                if (vow == null) { clearProjection(userId, "project_vow", id); continue }
                val count = count(vow.baselineCount, vow.targetJapaCount) { vowEntries(userId, id, it) }
                val complete = count >= vow.targetJapaCount
                feedback = feedback.copy(
                    purashcharanaCompleted = feedback.purashcharanaCompleted || (vow.completedAtMillis == null && complete),
                    completionReversed = feedback.completionReversed || (vow.completedAtMillis != null && !complete),
                    milestonesCrossed = feedback.milestonesCrossed + JapaAccounting.crossed(vow.currentJapaCount, count, vow.mantraAksharaCount).size)
                val updated = vow.copy(currentJapaCount = count,
                    completedAtMillis = if (complete) vow.completedAtMillis ?: now else null)
                if (updated != vow) putVow(updated)
                // Completion metadata can itself create a marker. This transaction has
                // already reconciled that value, so leave no self-generated follow-up.
                clearProjection(userId, "project_vow", id)
            }
        } while (ids.size == 200)
        return feedback
    }

    /** Every addition has an identity. Absolute corrections revise the original allocations. */
    suspend fun recordSandhya(change: SandhyaCountChangeEntity): JapaGoalFeedback {
        val old = periodPage(change.userId, change.dateKey, change.period)
        val delta = change.quantity - (change.previousQuantity ?: 0)
        if (delta > 0 || old.isEmpty()) {
            val fresh = JapaEntryEntity(change.id, change.userId, "gayatri", change.dateKey, change.zoneId,
                change.period, delta.coerceAtLeast(0), 1, change.createdAtMillis, null, null, "SANDHYA")
            // Raising an old total is a correction of the last entry, not enrollment in today's goal.
            val previous = old.firstOrNull()
            if (change.mode == "EDIT" && previous != null) {
                putEntry(previous.copy(quantity = Math.addExact(previous.quantity, delta), revision = previous.revision + 1, updatedAtMillis = change.createdAtMillis, operationId = change.id))
            } else putEntry(allocate(fresh))
        } else if (delta < 0) {
            var remaining = -delta
            var page = old
            while (page.isNotEmpty() && remaining > 0) {
                for (entry in page) {
                    val removed = minOf(remaining, entry.quantity)
                    if (removed > 0) putEntry(entry.copy(quantity = entry.quantity - removed, revision = entry.revision + 1, updatedAtMillis = change.createdAtMillis, operationId = change.id))
                    remaining -= removed
                    if (remaining == 0L) break
                }
                if (remaining > 0) {
                    val last = page.last()
                    page = periodPage(change.userId, change.dateKey, change.period, last.createdAtMillis, last.id)
                }
            }
            check(remaining == 0L) { "The count history needs to be restored before this correction" }
        }
        return project(change.userId, change.createdAtMillis, repair = false)
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
        project(session.userId, session.createdAt, repair = false)
    }
}
