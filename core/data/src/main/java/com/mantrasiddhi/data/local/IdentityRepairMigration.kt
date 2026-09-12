package com.mantrasiddhi.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.util.UUID

/** Repair before constraints. Ambiguous originals remain recoverable, never silently discarded. */
internal object IdentityRepairMigration : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS identity_repair_archive (tableName TEXT NOT NULL, rowId TEXT NOT NULL, userId INTEGER NOT NULL, payload TEXT NOT NULL, reason TEXT NOT NULL, PRIMARY KEY(tableName,rowId))")
        // An orphan profile ID is not evidence that its history belongs to another account.
        // Retain it and its diagnostic snapshot; do not invent a profile or cascade-delete it.
        for (table in listOf("targets", "purashcharana", "recitation_sessions", "japa_entries")) {
            for (id in ids(db, "SELECT id FROM $table WHERE NOT EXISTS (SELECT 1 FROM user_profiles p WHERE p.id=$table.userId)")) {
                archive(db, table, id, "Missing legacy profile; original ownership retained")
            }
        }
        // Future auto-generated profiles must not inherit a dormant orphan's numeric ID.
        val greatestOwner = db.query("SELECT MAX(userId) FROM (SELECT userId FROM targets UNION ALL SELECT userId FROM purashcharana UNION ALL SELECT userId FROM recitation_sessions UNION ALL SELECT userId FROM japa_entries)").use {
            it.moveToFirst(); if (it.isNull(0)) 0L else it.getLong(0)
        }
        db.execSQL("UPDATE sqlite_sequence SET seq=MAX(seq,?) WHERE name='user_profiles'", arrayOf(greatestOwner))
        db.execSQL("INSERT INTO sqlite_sequence(name,seq) SELECT 'user_profiles',? WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name='user_profiles')", arrayOf(greatestOwner))
        for (table in listOf("targets", "purashcharana", "recitation_sessions")) repairIdentities(db, table)
        db.execSQL("ALTER TABLE japa_entries ADD COLUMN sessionStableId TEXT")
        // A local session reference is immediate; goal identities and cloud session identities are eventual.
        val invalid = ids(db, "SELECT e.id FROM japa_entries e LEFT JOIN recitation_sessions s ON s.id=e.sessionId WHERE e.sessionId IS NOT NULL AND (s.id IS NULL OR s.userId!=e.userId OR s.mantraId!=e.mantraId OR s.mode!='NORMAL' OR s.isCorrect!=1 OR e.source!='AUDIO_ACCEPTED' OR e.quantity!=1 OR e.period IS NOT NULL OR s.createdAt!=e.createdAtMillis)")
        for (id in invalid) archiveEntry(db, id, "Invalid legacy session association")
        val duplicates = ids(db, "SELECT e.id FROM japa_entries e WHERE e.sessionId IS NOT NULL AND EXISTS (SELECT 1 FROM japa_entries winner WHERE winner.userId=e.userId AND winner.sessionId=e.sessionId AND (winner.createdAtMillis<e.createdAtMillis OR (winner.createdAtMillis=e.createdAtMillis AND winner.id<e.id)))")
        for (id in duplicates) archiveEntry(db, id, "Duplicate credit for one session; earliest entry retained")
        db.execSQL("UPDATE japa_entries SET sessionStableId=(SELECT stableId FROM recitation_sessions WHERE id=japa_entries.sessionId) WHERE sessionId IS NOT NULL")
        db.execSQL("ALTER TABLE recitation_sessions ADD COLUMN acceptedEntryId TEXT")
        db.execSQL("UPDATE recitation_sessions SET acceptedEntryId=(SELECT id FROM japa_entries WHERE sessionId=recitation_sessions.id AND userId=recitation_sessions.userId LIMIT 1)")
        // v14 kept a session-first restore's backlink only in its durable fingerprint.
        // Carry that unresolved reference forward even if the ledger page has not arrived.
        db.query("SELECT s.id,s.userId,s.stableId,b.payload FROM recitation_sessions s JOIN user_profiles p ON p.id=s.userId JOIN backup_queue_state b ON b.uid=p.firebaseUid AND b.`key`='practice_record:session:'||s.stableId WHERE s.acceptedEntryId IS NULL AND s.mode='NORMAL' AND s.isCorrect=1 ORDER BY s.id").use { rows ->
            while (rows.moveToNext()) {
                val json = runCatching { JSONObject(rows.getString(3)) }.getOrNull() ?: continue
                val entry = json.opt("entryId") as? String ?: continue
                if (entry.isBlank() || json.optString("id") != rows.getString(2)) continue
                val conflict = db.query("SELECT 1 FROM recitation_sessions WHERE userId=? AND acceptedEntryId=? UNION ALL SELECT 1 FROM japa_entries WHERE id=?", arrayOf(rows.getLong(1), entry, entry)).use { it.moveToFirst() }
                // Existing associations were already resolved above. Conflicting legacy claims
                // remain in the original fingerprint for explicit repair, never overwrite one.
                if (conflict) continue
                db.execSQL("UPDATE recitation_sessions SET acceptedEntryId=? WHERE id=?", arrayOf(entry, rows.getLong(0)))
            }
        }
        db.execSQL("CREATE UNIQUE INDEX index_recitation_sessions_userId_acceptedEntryId ON recitation_sessions(userId,acceptedEntryId)")
        for (table in listOf("targets", "purashcharana", "recitation_sessions")) {
            db.execSQL("DROP INDEX IF EXISTS index_${table}_userId_stableId")
            db.execSQL("CREATE UNIQUE INDEX index_${table}_userId_stableId ON $table(userId,stableId)")
        }
        db.execSQL("CREATE UNIQUE INDEX index_japa_entries_userId_sessionId ON japa_entries(userId,sessionId)")
        db.execSQL("CREATE UNIQUE INDEX index_japa_entries_userId_sessionStableId ON japa_entries(userId,sessionStableId)")
        // Recompute projections only for owners whose ledger was repaired, without allocating old entries anew.
        val owners = ids(db, "SELECT DISTINCT userId FROM identity_repair_archive WHERE tableName='japa_entries'")
        for (owner in owners) for ((table, column, count, cap) in listOf(
            listOf("targets", "targetId", "currentRecitationCount", "targetRecitationCount"),
            listOf("purashcharana", "vowId", "currentJapaCount", "targetJapaCount"))) {
            db.query("SELECT id, stableId, baselineCount, $cap FROM $table WHERE userId=?", arrayOf(owner)).use { goals ->
                while (goals.moveToNext()) {
                    var total = java.math.BigInteger.valueOf(goals.getLong(2))
                    db.query("SELECT quantity FROM japa_entries WHERE userId=? AND $column=?", arrayOf(owner, goals.getString(1))).use { entries ->
                        while (entries.moveToNext()) total += java.math.BigInteger.valueOf(entries.getLong(0))
                    }
                    val value = total.max(java.math.BigInteger.ZERO).min(java.math.BigInteger.valueOf(goals.getLong(3))).toLong()
                    db.execSQL("UPDATE $table SET $count=? WHERE id=?", arrayOf(value, goals.getLong(0)))
                    if (table == "targets") db.execSQL("UPDATE targets SET isCompleted=(currentRecitationCount>=targetRecitationCount) WHERE id=?", arrayOf(goals.getLong(0)))
                    else db.execSQL("UPDATE purashcharana SET completedAtMillis=NULL WHERE id=? AND currentJapaCount<targetJapaCount", arrayOf(goals.getLong(0)))
                }
            }
        }
    }

    private fun repairIdentities(db: SupportSQLiteDatabase, table: String) {
        val order = if (table == "recitation_sessions") "id" else "isActive DESC, startedAtMillis DESC, id"
        val seen = mutableSetOf<Pair<Long, String>>()
        val rows = db.query("SELECT id,userId,stableId FROM $table ORDER BY $order").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getLong(1), c.getString(2))) }
        }
        for (group in rows.groupBy { it.second to it.third }.values) {
            val preferred = if (table == "recitation_sessions") group.first() else
                group.firstOrNull { matchesQueuedGoal(db, table, it.first, it.second, it.third) } ?: group.first()
            for ((id, user, old) in listOf(preferred) + group.filter { it.first != preferred.first }) {
                if (old.isNotBlank() && seen.add(user to old)) continue
                archive(db, table, id.toString(), "Identity collision/empty identity; canonical keeps existing cloud ID")
                if (table == "recitation_sessions" && old.isNotBlank()) {
                    val columns = listOf("userId", "mantraId", "mode", "overallScore", "wordScoresJson", "transcription", "isCorrect", "durationMs", "createdAt", "correctness", "completeness", "pronunciation")
                    val same = columns.joinToString(" AND ") { "a.$it=b.$it" }
                    val canonical = db.query("SELECT a.id FROM recitation_sessions a JOIN recitation_sessions b ON b.id=? WHERE a.userId=? AND a.stableId=? AND a.id!=b.id AND $same ORDER BY a.id LIMIT 1", arrayOf(id, user, old)).use { if (it.moveToFirst()) it.getLong(0) else null }
                    if (canonical != null) {
                        for (entry in ids(db, "SELECT id FROM japa_entries WHERE sessionId=$id")) archive(db, "japa_entries", entry, "Duplicate immutable session merged into canonical assessment")
                        db.execSQL("UPDATE japa_entries SET sessionId=? WHERE sessionId=?", arrayOf(canonical, id))
                        db.execSQL("DELETE FROM recitation_sessions WHERE id=?", arrayOf(id))
                        continue
                    }
                }
                var salt = 0
                var replacement: String
                val definition = definition(db, table, id)
                do {
                    // Local profile/row numbers differ across devices. Derive compatibility IDs
                    // from the definition; the ordinal distinguishes identical historical rows.
                    replacement = UUID.nameUUIDFromBytes("v15:$table:$old:$definition:${salt++}".toByteArray(Charsets.UTF_8)).toString()
                } while (db.query("SELECT 1 FROM $table WHERE userId=? AND stableId=?", arrayOf(user, replacement)).use { it.moveToFirst() })
                if (table == "recitation_sessions") {
                    db.execSQL("UPDATE $table SET stableId=? WHERE id=?", arrayOf(replacement, id))
                } else {
                    val count = if (table == "targets") "currentRecitationCount" else "currentJapaCount"
                    val column = if (table == "targets") "targetId" else "vowId"
                    val mantra = db.query("SELECT mantraId FROM $table WHERE id=?", arrayOf(id)).use { it.moveToFirst(); it.getString(0) }
                    val otherSameMantra = db.query("SELECT 1 FROM $table WHERE userId=? AND stableId=? AND mantraId=? AND id!=?", arrayOf(user, old, mantra, id)).use { it.moveToFirst() }
                    if (!otherSameMantra) {
                        // A different mantra disambiguates the original allocation without guessing.
                        for (entry in db.query("SELECT id FROM japa_entries WHERE userId=? AND $column=? AND mantraId=?", arrayOf(user, old, mantra)).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }) {
                            archive(db, "japa_entries", entry, "Goal allocation disambiguated by mantra")
                            db.execSQL("UPDATE japa_entries SET $column=? WHERE id=?", arrayOf(replacement, entry))
                        }
                    }
                    // Shared old allocations stay on the canonical identity. Freeze the other historical
                    // display at its recorded count; close at start to prevent any fresh allocation.
                    db.execSQL("UPDATE $table SET stableId=?, baselineCount=${if (otherSameMantra) count else "baselineCount"}, isActive=0, endedAtMillis=startedAtMillis WHERE id=?", arrayOf(replacement, id))
                }
                seen.add(user to replacement)
            }
        }
    }

    private fun definition(db: SupportSQLiteDatabase, table: String, id: Long): String {
        val columns = when (table) {
            "targets" -> "mantraId,targetRecitationCount,rewardType,rewardDescription,startedAtMillis"
            "purashcharana" -> "mantraId,mantraAksharaCount,targetJapaCount,startedAtMillis"
            else -> "mantraId,mode,overallScore,wordScoresJson,transcription,isCorrect,durationMs,createdAt,correctness,completeness,pronunciation"
        }
        return db.query("SELECT $columns FROM $table WHERE id=?", arrayOf(id)).use { c ->
            c.moveToFirst()
            org.json.JSONArray().apply { for (i in 0 until c.columnCount) put(c.getString(i)) }.toString()
        }
    }

    private fun matchesQueuedGoal(db: SupportSQLiteDatabase, table: String, id: Long, user: Long, stableId: String): Boolean {
        val payload = db.query("SELECT b.payload FROM backup_queue_state b JOIN user_profiles p ON p.firebaseUid=b.uid WHERE p.id=? AND b.`key`=?", arrayOf(user, "japa_goal::$stableId")).use { if (it.moveToFirst()) it.getString(0) else null } ?: return false
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return false
        val target = table == "targets"
        if (json.optString("kind") != if (target) "target" else "vow") return false
        val fields = linkedMapOf("mantraId" to "mantraId", "startedAtMillis" to "startedAtMillis", "baselineCount" to "baselineCount",
            "targetCount" to if (target) "targetRecitationCount" else "targetJapaCount")
        if (target) { fields["rewardType"] = "rewardType"; fields["rewardDescription"] = "rewardDescription" }
        else fields["aksharas"] = "mantraAksharaCount"
        return db.query("SELECT ${fields.values.joinToString(",")} FROM $table WHERE id=?", arrayOf(id)).use { c ->
            c.moveToFirst(); fields.keys.withIndex().all { (i, key) -> json.has(key) && json.get(key).toString() == c.getString(i) }
        }
    }

    private fun ids(db: SupportSQLiteDatabase, sql: String): List<String> = db.query(sql).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }

    private fun archiveEntry(db: SupportSQLiteDatabase, id: String, reason: String) {
        archive(db, "japa_entries", id, reason)
        db.execSQL("DELETE FROM japa_entries WHERE id=?", arrayOf(id))
    }

    private fun archive(db: SupportSQLiteDatabase, table: String, id: String, reason: String) {
        db.query("SELECT * FROM $table WHERE id=?", arrayOf(id)).use { c ->
            check(c.moveToFirst())
            val json = JSONObject()
            for (i in 0 until c.columnCount) json.put(c.getColumnName(i), when (c.getType(i)) {
                android.database.Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                else -> c.getString(i)
            })
            db.execSQL("INSERT OR IGNORE INTO identity_repair_archive VALUES (?,?,?,?,?)", arrayOf(table, id, c.getLong(c.getColumnIndexOrThrow("userId")), json.toString(), reason))
        }
    }
}
