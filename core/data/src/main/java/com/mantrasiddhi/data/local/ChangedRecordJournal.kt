package com.mantrasiddhi.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** All writers, including raw migrations and reminder DAOs, share the same durable boundary. */
object ChangedRecordJournal : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) = install(db)

    fun install(db: SupportSQLiteDatabase) {
        // An outer INSERT/UPDATE OR ABORT overrides a trigger's OR IGNORE. Exclude an
        // existing key before INSERT so Room's conflict policy cannot break coalescing.
        fun event(kind: String, id: String, row: String = "NEW", condition: String = "1") =
            "INSERT INTO changed_records(userId,kind,recordId) SELECT $row.userId,'$kind',$id WHERE ($condition) " +
                "AND NOT EXISTS (SELECT 1 FROM changed_records WHERE userId=$row.userId AND kind='$kind' AND recordId=$id);"
        fun triggers(table: String, kind: String, columns: List<String>, extra: (String) -> String = { "" }) {
            for (operation in listOf("INSERT", "UPDATE")) {
                val changed = if (operation == "UPDATE") "WHEN " + columns.joinToString(" OR ") { "OLD.$it IS NOT NEW.$it" } else ""
                db.execSQL("CREATE TRIGGER IF NOT EXISTS journal_${table}_${operation.lowercase()} AFTER $operation ON $table $changed BEGIN " +
                    event(kind, "CAST(NEW.id AS TEXT)") + extra(operation) + " END")
            }
        }
        triggers("recitation_sessions", "session", listOf("stableId", "acceptedEntryId", "projectionDate", "projectionZoneId", "childGoalEligible"))
        triggers("sandhya_logs", "log", listOf("status", "observedEpochMillis", "scheduledEpochMillis"))
        triggers("sandhya_count_changes", "change", listOf("quantity", "revision"))
        triggers("japa_entries", "entry", listOf("quantity", "revision", "targetId", "vowId", "sessionId", "sessionStableId", "operationId")) { operation ->
            buildString {
                for (row in if (operation == "UPDATE") listOf("OLD", "NEW") else listOf("NEW")) {
                    append(event("project_target", "$row.targetId", row, "$row.targetId IS NOT NULL"))
                    append(event("project_vow", "$row.vowId", row, "$row.vowId IS NOT NULL"))
                    append(event("period", "$row.dateKey || ':' || $row.period", row, "$row.period IS NOT NULL"))
                }
                append(event("allocate", "NEW.id", condition = "NEW.targetId IS NULL AND NEW.createdAtMillis > 0"))
            }
        }
        for ((table, kind) in listOf("targets" to "target", "purashcharana" to "vow")) {
            val columns = listOf("stableId", "mantraId", "startedAtMillis", "endedAtMillis", "baselineCount") +
                if (kind == "target") listOf("setByUserId", "targetRecitationCount", "rewardType", "rewardDescription")
                else listOf("mantraAksharaCount", "targetJapaCount", "completedAtMillis")
            triggers(table, kind, columns)
            for (operation in listOf("INSERT", "UPDATE")) {
                val projectionColumns = listOf("stableId", "mantraId", "startedAtMillis", "baselineCount", "endedAtMillis") +
                    if (kind == "target") listOf("targetRecitationCount") else listOf("targetJapaCount", "completedAtMillis")
                val changed = if (operation == "UPDATE") "WHEN " + projectionColumns.joinToString(" OR ") { "OLD.$it IS NOT NEW.$it" } else ""
                db.execSQL("CREATE TRIGGER IF NOT EXISTS projection_${kind}_${operation.lowercase()} AFTER $operation ON $table $changed BEGIN " +
                    event("project_$kind", "NEW.stableId") + (if (kind == "target") event("repair", "'targets'") else "") + " END")
            }
        }
    }
}
