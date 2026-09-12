package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.entity.BackupConflictEntity
import com.mantrasiddhi.data.local.entity.BackupQuarantineEntity
import java.math.BigInteger
import org.json.JSONObject

/** Caller owns the page transaction: accepted rows, quarantine and cursor commit together. */
internal class BackupRecordRestorer(private val database: MantraSiddhiDatabase, private val backup: PracticeBackupStore) {
    suspend fun apply(uid: String, userId: Long, source: String, type: String, id: String,
        value: Map<String, Any?>, ownerStillActive: () -> Boolean, envelopeError: String? = null): Boolean {
        check(ownerStillActive())
        val reason = envelopeError ?: BackupRecordValidator.validate(type, id, value)
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
                database.backupDao().putConflict(BackupConflictEntity(uid, id, value.text("entryId"),
                    JSONObject(first).toString(), JSONObject(second).toString(), previous?.resolved == true || value["resolved"] == true))
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
            // Long is the exact domain bound for a manual period, including merged replicas.
            // Preserve excess input in quarantine; never clamp or truncate performed quantities.
            if (value["period"] != null && JapaSyncPolicy.newer(value, old?.payload())) {
                val total = database.japaDao().periodEntries(userId, value.text("dateKey"), value.text("period"))
                    .filter { it.id != value.text("id") }.fold(BigInteger.valueOf(value.number("quantity"))) { sum, e -> sum + BigInteger.valueOf(e.quantity) }
                if (total > BigInteger.valueOf(Long.MAX_VALUE)) return "Merged period exceeds Long.MAX_VALUE; correct the source count and sync again"
            }
        }
        if (type == "practice") {
            if (value["type"] == "session") {
                val entry = database.japaDao().entry(value.text("entryId"))
                if (entry != null && entry.userId != userId) return "Session entry belongs to another local profile"
            }
            if (value["type"] == "change") {
                val old = database.sandhyaCountDao().getChange(value.text("id"))
                if (old != null && old.userId != userId) return "Change identity belongs to another local profile"
            }
        }
        return null
    }
}
