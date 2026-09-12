package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.entity.BackupConflictEntity
import com.mantrasiddhi.data.local.entity.BackupQuarantineEntity
import com.mantrasiddhi.data.local.entity.JapaEntryEntity
import java.math.BigInteger
import org.json.JSONObject

/** Caller owns the page transaction: accepted rows, quarantine and cursor commit together. */
internal class BackupRecordRestorer(private val database: MantraSiddhiDatabase, private val backup: PracticeBackupStore) {
    suspend fun apply(uid: String, userId: Long, source: String, type: String, id: String,
        value: Map<String, Any?>, ownerStillActive: () -> Boolean, envelopeError: String? = null): Boolean {
        check(ownerStillActive())
        val ownerError = if (database.userDao().getById(userId)?.firebaseUid != uid) "Restore owner does not match local profile" else null
        val reason = envelopeError ?: ownerError ?: BackupRecordValidator.validate(type, id, value)
            ?: localRejection(userId, type, value)
        if (reason != null) {
            database.backupDao().quarantine(BackupQuarantineEntity(uid, source, type, id, JSONObject(safeJson(value) as Map<*, *>).toString(), reason))
            return false
        }
        when (type) {
            "entry" -> JapaReplicaMerger(database).merge(userId, emptyList(), listOf(value), ownerStillActive, project = false)
            "goal" -> JapaReplicaMerger(database).merge(userId, listOf(value), emptyList(), ownerStillActive, project = false)
            "practice" -> backup.merge(userId, value)
            "conflict" -> {
                val previous = database.backupDao().conflict(uid, id)
                @Suppress("UNCHECKED_CAST") val first = value["first"] as Map<String, Any?>
                @Suppress("UNCHECKED_CAST") val second = value["second"] as Map<String, Any?>
                val conflict = BackupConflictEntity(uid, id, value.text("entryId"),
                    JSONObject(first).toString(), JSONObject(second).toString(), previous?.resolved == true || value["resolved"] == true)
                if (conflict != previous) database.backupDao().putConflict(conflict)
            }
        }
        database.backupDao().clearQuarantine(uid, source)
        val collection = when (type) { "entry" -> "japaEntries"; "goal" -> "japaGoals"; "practice" -> "practiceRecords"; else -> "japaConflicts" }
        database.backupDao().clearQuarantine(uid, "$collection/$id")
        return true
    }

    // Firestore permits NaN/infinity, timestamps and other non-JSON values. Preserve their
    // diagnostic representation without letting JSON serialization poison the page either.
    private fun safeJson(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.mapValues { safeJson(it.value) }
        is List<*> -> value.map { safeJson(it) }
        is Double -> if (value.isFinite()) value else mapOf("nonFiniteDouble" to value.toString())
        is Float -> if (value.isFinite()) value else mapOf("nonFiniteFloat" to value.toString())
        else -> value
    }

    private suspend fun localRejection(userId: Long, type: String, value: Map<String, Any?>): String? {
        if (type == "entry") {
            val old = database.japaDao().entry(value.text("id"))
            if (old != null && old.userId != userId) return "Entry identity belongs to another local profile"
            val session = database.backupDao().session(userId, value.text("sessionStableId"))
                ?: database.backupDao().sessionForEntry(userId, value.text("id"))
            val incoming = JapaEntryEntity(value.text("id"), userId, value.text("mantraId"), value.text("dateKey"),
                value.text("zoneId"), value["period"] as? String, value.number("quantity"), value.number("revision"),
                value.number("createdAtMillis"), value["targetId"] as? String, value["vowId"] as? String,
                value.text("source"), session?.id ?: old?.sessionId, value.number("updatedAtMillis"), value.text("operationId"),
                value["sessionStableId"] as? String ?: old?.sessionStableId ?: session?.stableId)
            // A stale revision does not undo local allocation repair. Only its missing
            // session association can be completed, matching the merger's write path.
            val candidate = if (old == null || JapaSyncPolicy.newer(value, old.payload())) incoming
                else old.copy(sessionId = old.sessionId ?: session?.id, sessionStableId = old.sessionStableId ?: session?.stableId)
            database.japaDao().entryRejection(candidate)?.let { return it }
            // Long is the exact domain bound for a manual period, including merged replicas.
            // Preserve excess input in quarantine; never clamp or truncate performed quantities.
            if (value["period"] != null && JapaSyncPolicy.newer(value, old?.payload())) {
                var time = Long.MAX_VALUE
                var after = "\uffff"
                var total = BigInteger.valueOf(value.number("quantity"))
                do {
                    val page = database.japaDao().periodPage(userId, value.text("dateKey"), value.text("period"), time, after)
                    for (entry in page) if (entry.id != value.text("id")) total += BigInteger.valueOf(entry.quantity)
                    page.lastOrNull()?.let { time = it.createdAtMillis; after = it.id }
                } while (page.size == 200)
                if (total > BigInteger.valueOf(Long.MAX_VALUE)) return "Merged period exceeds Long.MAX_VALUE; correct the source count and sync again"
            }
        }
        if (type == "goal") {
            val target = value["kind"] == "target"
            val id = value.text("id")
            val mantra = value.text("mantraId")
            val oldMantra = if (target) database.targetDao().getByStableId(userId, id)?.mantraId
                else database.japaDao().vow(userId, id)?.mantraId
            if (oldMantra != null && oldMantra != mantra) return "Goal mantra identity is immutable"
            var after = ""
            do {
                val page = if (target) database.japaDao().targetEntries(userId, id, after)
                    else database.japaDao().vowEntries(userId, id, after)
                if (page.any { it.mantraId != mantra }) return "Goal disagrees with an existing entry allocation"
                after = page.lastOrNull()?.id ?: after
            } while (page.size == 200)
        }
        if (type == "practice") {
            if (value["type"] == "session") {
                val old = database.backupDao().session(userId, value.text("id"))
                val session = PracticeRecordCodec.session(value, userId, old?.id ?: 0)
                if (old != null && old != session.copy(acceptedEntryId = old.acceptedEntryId)) return "Saved assessments are immutable"
                if (old?.acceptedEntryId != null && session.acceptedEntryId != null && old.acceptedEntryId != session.acceptedEntryId) return "Session entry identity is immutable"
                val existingClaim = database.backupDao().sessionForEntry(userId, value.text("entryId"))
                if (existingClaim != null && existingClaim.stableId != session.stableId) return "Entry is already claimed by another session"
                val namedEntry = database.japaDao().entry(value.text("entryId"))
                val pending = database.japaDao().audioEntryByStableId(userId, value.text("id"))
                    ?: old?.let { database.japaDao().audioEntry(userId, it.id) }
                if (value["entryId"] != null && pending != null && pending.id != value["entryId"]) return "Session already has a different accepted entry"
                if (value["entryId"] != null && (!session.isCorrect || session.mode != "NORMAL")) return "Ineligible assessment cannot claim an accepted entry"
                val entry = namedEntry ?: pending
                if (entry != null && entry.userId != userId) return "Session entry belongs to another local profile"
                if (entry != null) database.japaDao().sessionRejection(entry, session)?.let { return it }
            }
            if (value["type"] == "change") {
                val old = database.sandhyaCountDao().getChange(value.text("id"))
                if (old != null && old.userId != userId) return "Change identity belongs to another local profile"
            }
        }
        return null
    }
}
