package com.mantrasiddhi.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.mantrasiddhi.data.local.entity.JapaEntryEntity
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class IdentityMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        MantraSiddhiDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    private val migrations = with(MantraSiddhiDatabase) { arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17) }

    @Test fun everySupportedStartingVersionPreservesMixedHistoryThroughProductionChain() {
        for (version in 1..16) {
            val name = "identity-chain-$version-${UUID.randomUUID()}"
            try {
                // v1-v3 predate committed Room exports. Reconstruct only their documented
                // DDL differences from v4, then run the real migrations and v15 validation.
                if (version < 4) createEarlyDatabase(name, version)
                else helper.createDatabase(name, version).close()
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    for (owner in listOf(7, 8)) insert(db, "user_profiles", mapOf("id" to owner, "name" to "Owner $owner", "profileType" to "INDIVIDUAL", "parentId" to null, "avatarIndex" to 2))
                    for (id in 1..4) insert(db, "targets", mapOf("id" to id, "userId" to if (id == 4) 8 else 7, "setByUserId" to 7,
                        "targetRecitationCount" to 108, "currentRecitationCount" to if (id == 3) 108 else 32, "moneyAmount" to 5.0,
                        "rewardType" to "OTHER", "rewardDescription" to "history", "isCompleted" to if (id == 3) 1 else 0,
                        "stableId" to if (id == 3) "complete" else if (version >= 15 && id == 2) "second-target" else "legacy-target", "baselineCount" to if (id == 3) 108 else 32, "startedAtMillis" to 100))
                    for (id in 1..2) insert(db, "recitation_sessions", mapOf("id" to id, "userId" to 7, "mantraId" to "gayatri",
                        "mode" to if (id == 1) "NORMAL" else "PRACTICE", "overallScore" to .75, "wordScoresJson" to "om:0.75",
                        "transcription" to "om", "isCorrect" to 1, "durationMs" to 5000, "createdAt" to 1700000000000L,
                        "correctness" to .75, "completeness" to .75, "pronunciation" to .75, "stableId" to "session-$id"))
                    if (version >= 6) for (id in 1..2) insert(db, "purashcharana", mapOf("id" to id, "userId" to 7,
                        "mantraId" to "gayatri", "mantraAksharaCount" to 24, "targetJapaCount" to 2400000, "currentJapaCount" to 500,
                        "startedAtMillis" to 100, "completedAtMillis" to null, "isActive" to 1, "stableId" to if (version >= 15) "vow-$id" else "legacy-vow-100", "baselineCount" to 500))
                    if (version >= 4) insert(db, "sandhya_logs", mapOf("id" to 1, "userId" to 7, "period" to "SAYAM", "scheduledEpochMillis" to 100,
                        "status" to "OBSERVED", "observedEpochMillis" to 101, "dateKey" to "20260910"))
                    if (version >= 9) {
                        insert(db, "cloud_sync_outbox", mapOf("id" to 1, "uid" to "owner", "streamId" to "device", "kind" to "progress", "payload" to "{}", "createdAtMs" to 10))
                        insert(db, "cloud_sync_schedule", mapOf("uid" to "owner", "nextAttemptAtMs" to 1234))
                    }
                    if (version >= 11) insert(db, "sandhya_counts", mapOf("userId" to 7, "dateKey" to "20260910", "period" to "SAYAM", "quantity" to 108, "revision" to 1, "zoneId" to "UTC"))
                    if (version >= 12) insert(db, "japa_entries", mapOf("id" to "manual", "userId" to 7, "mantraId" to "gayatri", "dateKey" to "20260910", "zoneId" to "UTC",
                        "period" to "SAYAM", "quantity" to 108, "revision" to 1, "createdAtMillis" to 100, "targetId" to null, "vowId" to null,
                        "source" to "SANDHYA", "sessionId" to null, "updatedAtMillis" to 100, "operationId" to "manual"))
                }
                helper.runMigrationsAndValidate(name, 17, true, *migrations).use { db ->
                    assertEquals("v$version profiles", 2L, number(db, "SELECT COUNT(*) FROM user_profiles"))
                    assertEquals(2L, number(db, "SELECT COUNT(*) FROM recitation_sessions"))
                    assertEquals(10000L, number(db, "SELECT SUM(durationMs) FROM recitation_sessions"))
                    assertEquals(4L, number(db, "SELECT COUNT(*) FROM targets"))
                    assertEquals(32L, number(db, "SELECT currentRecitationCount FROM targets WHERE id=2"))
                    assertEquals(108L, number(db, "SELECT currentRecitationCount FROM targets WHERE id=3"))
                    assertEquals(0L, number(db, "SELECT COUNT(*) FROM (SELECT userId,stableId FROM targets GROUP BY userId,stableId HAVING COUNT(*)>1)"))
                    assertEquals(1L, number(db, "SELECT COUNT(*) FROM targets WHERE userId=7 AND stableId='legacy-target'"))
                    if (version >= 6) assertEquals(1000L, number(db, "SELECT SUM(currentJapaCount) FROM purashcharana"))
                    if (version >= 4) assertEquals(101L, number(db, "SELECT observedEpochMillis FROM sandhya_logs"))
                    if (version >= 9) {
                        assertEquals(1L, number(db, "SELECT COUNT(*) FROM cloud_sync_outbox"))
                        assertEquals(1234L, number(db, "SELECT nextAttemptAtMs FROM cloud_sync_schedule"))
                    }
                    if (version >= 11) assertEquals(108L, number(db, "SELECT SUM(quantity) FROM japa_entries"))
                }
                // Open/reopen through the actual production builder as well as the schema helper.
                repeat(2) { MantraSiddhiDatabase.buildDatabase(context, name).let { db ->
                    try { assertEquals(17, db.openHelper.writableDatabase.version) } finally { db.close() }
                } }
            } finally { context.deleteDatabase(name) }
        }
    }

    @Test fun duplicateSessionsCreditsAndInvalidAssociationsAreArchivedBeforeUniqueIndexes() {
        val name = "identity-duplicates-${UUID.randomUUID()}"
        try {
            helper.createDatabase(name, 14).use { db ->
                db.execSQL("INSERT INTO user_profiles (id,name,profileType,parentId,avatarIndex) VALUES (7,'Owner','INDIVIDUAL',NULL,0),(8,'Other','INDIVIDUAL',NULL,0)")
                for (id in 1..4) db.execSQL("INSERT INTO recitation_sessions (id,userId,mantraId,mode,overallScore,wordScoresJson,transcription,isCorrect,durationMs,createdAt,correctness,completeness,pronunciation,stableId) VALUES (?,?,'gayatri','NORMAL',?,'om:0.9','om',1,1000,100,0.9,0.9,0.9,'session')", arrayOf(id, if (id == 4) 8 else 7, if (id == 3) .5 else .9))
                for ((id, session, owner) in listOf(Triple("a", 1, 7), Triple("b", 1, 7), Triple("c", 2, 7), Triple("foreign", 4, 7), Triple("orphan", 999, 7))) {
                    db.execSQL("INSERT INTO japa_entries VALUES (?,?, 'gayatri','19700101','UTC',NULL,1,1,100,NULL,NULL,'AUDIO_ACCEPTED',?,100,?)", arrayOf(id, owner, session, id))
                }
            }
            helper.runMigrationsAndValidate(name, 15, true, MantraSiddhiDatabase.MIGRATION_14_15).use { db ->
                assertEquals(3L, number(db, "SELECT COUNT(*) FROM recitation_sessions"))
                assertEquals(1L, number(db, "SELECT COUNT(*) FROM japa_entries"))
                assertEquals(1L, number(db, "SELECT quantity FROM japa_entries WHERE id='a'"))
                assertEquals(4L, number(db, "SELECT COUNT(*) FROM identity_repair_archive WHERE tableName='japa_entries'"))
                assertEquals(2L, number(db, "SELECT COUNT(*) FROM identity_repair_archive WHERE tableName='recitation_sessions'"))
                db.query("SELECT payload FROM identity_repair_archive WHERE tableName='japa_entries' AND rowId='foreign'").use {
                    assertTrue(it.moveToFirst()); assertEquals(4, JSONObject(it.getString(0)).getInt("sessionId"))
                }
                assertTrue(runCatching { db.execSQL("INSERT INTO japa_entries SELECT 'duplicate',userId,mantraId,dateKey,zoneId,period,quantity,revision,createdAtMillis,targetId,vowId,source,sessionId,updatedAtMillis,operationId,sessionStableId FROM japa_entries") }.isFailure)
                assertEquals(1L, number(db, "SELECT COUNT(*) FROM recitation_sessions WHERE userId=8 AND stableId='session'"))
            }
        } finally { context.deleteDatabase(name) }
    }

    private fun number(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { assertTrue(sql, it.moveToFirst()); it.getLong(0) }

    @Test fun goalRepairPreservesCloudDefinitionAndAllocationsWithDeviceIndependentIdentities() = runBlocking {
        val repaired = mutableListOf<List<String>>()
        for ((owner, offset) in listOf(7 to 0, 17 to 10)) {
            val name = "identity-goals-${UUID.randomUUID()}"
            try {
                helper.createDatabase(name, 14).use { db ->
                    db.execSQL("INSERT INTO user_profiles (id,name,profileType,parentId,avatarIndex,firebaseUid) VALUES (?,'Owner','INDIVIDUAL',NULL,0,'owner')", arrayOf(owner))
                    for (n in 1..3) db.execSQL("INSERT INTO targets (id,userId,setByUserId,targetRecitationCount,currentRecitationCount,rewardType,rewardDescription,isCompleted,stableId,mantraId,startedAtMillis,baselineCount) VALUES (?,?,?, ?,?,'OTHER','',0,'legacy-target',?,100,0)", arrayOf(n+offset, owner, owner, n*10, if (n==3) 2 else 3, if (n==3) "other" else "gayatri"))
                    for ((id, mantra, quantity) in listOf(Triple("a", "gayatri", 3), Triple("b", "other", 2))) db.execSQL("INSERT INTO japa_entries VALUES (?, ?,?,'19700101','UTC','SAYAM',?,1,100,'legacy-target',NULL,'SANDHYA',NULL,100,?)", arrayOf(id, owner, mantra, quantity, id))
                    db.execSQL("INSERT INTO backup_queue_state VALUES ('owner','japa_goal::legacy-target',?)", arrayOf("{\"id\":\"legacy-target\",\"kind\":\"target\",\"mantraId\":\"gayatri\",\"startedAtMillis\":100,\"baselineCount\":0,\"targetCount\":20,\"rewardType\":\"OTHER\",\"rewardDescription\":\"\"}"))
                }
                helper.runMigrationsAndValidate(name, 15, true, MantraSiddhiDatabase.MIGRATION_14_15).close()
                val db = MantraSiddhiDatabase.buildDatabase(context, name)
                try {
                    val goals = db.japaDao().targets(owner.toLong()).sortedBy { it.id }
                    repaired += goals.map { it.stableId }
                    assertEquals("legacy-target", goals[1].stableId)
                    assertEquals(goals[2].stableId, db.japaDao().entry("b")!!.targetId)
                    val fresh = JapaEntryEntity("fresh", owner.toLong(), "gayatri", "19700101", "UTC", "SAYAM", 1, 1, 200, null, null, "SANDHYA")
                    db.japaDao().putEntry(db.japaDao().allocate(fresh))
                    db.japaDao().project(owner.toLong(), 200)
                    val projected = db.japaDao().targets(owner.toLong()).sortedBy { it.id }
                    assertEquals(listOf(3, 4, 2), projected.map { it.currentRecitationCount })
                    assertEquals(listOf(false, true, false), projected.map { it.isActive })
                    assertEquals(6L, db.japaDao().entries(owner.toLong()).sumOf { it.quantity })
                } finally { db.close() }
            } finally { context.deleteDatabase(name) }
        }
        assertEquals(repaired[0], repaired[1])
    }

    @Test fun orphanOwnershipAndUnresolvedLegacySessionClaimsSurviveUpgrade() {
        val name = "identity-orphans-${UUID.randomUUID()}"
        try {
            helper.createDatabase(name, 14).use { db ->
                db.execSQL("INSERT INTO user_profiles (id,name,profileType,parentId,avatarIndex,firebaseUid) VALUES (7,'Owner','INDIVIDUAL',NULL,0,'owner')")
                for ((id, owner) in listOf(1 to 7, 2 to 777)) db.execSQL("INSERT INTO recitation_sessions (id,userId,mantraId,mode,overallScore,wordScoresJson,transcription,isCorrect,durationMs,createdAt,correctness,completeness,pronunciation,stableId) VALUES (?,?,'gayatri','NORMAL',0.9,'om:0.9','om',1,1000,100,0.9,0.9,0.9,?)", arrayOf(id, owner, "session-$id"))
                db.execSQL("INSERT INTO backup_queue_state VALUES ('owner','practice_record:session:session-1',?)", arrayOf("{\"id\":\"session-1\",\"type\":\"session\",\"entryId\":\"pending-entry\"}"))
            }
            helper.runMigrationsAndValidate(name, 15, true, MantraSiddhiDatabase.MIGRATION_14_15).use { db ->
                assertEquals(777L, number(db, "SELECT userId FROM recitation_sessions WHERE id=2"))
                assertEquals(1L, number(db, "SELECT COUNT(*) FROM identity_repair_archive WHERE userId=777"))
                db.execSQL("INSERT INTO user_profiles (name,profileType,parentId,avatarIndex) VALUES ('New','INDIVIDUAL',NULL,0)")
                assertTrue(number(db, "SELECT id FROM user_profiles WHERE name='New'") > 777)
                db.query("SELECT acceptedEntryId FROM recitation_sessions WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals("pending-entry", it.getString(0)) }
                assertEquals(1L, number(db, "SELECT COUNT(*) FROM backup_queue_state"))
            }
        } finally { context.deleteDatabase(name) }
    }

    private fun insert(db: SQLiteDatabase, table: String, values: Map<String, Any?>) {
        val columns = db.rawQuery("PRAGMA table_info($table)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
        val selected = values.filterKeys { it in columns }
        db.execSQL("INSERT INTO $table (${selected.keys.joinToString(",")}) VALUES (${selected.keys.joinToString(",") { "?" }})", selected.values.toTypedArray())
    }

    private fun createEarlyDatabase(name: String, version: Int) {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("com.mantrasiddhi.data.local.MantraSiddhiDatabase/4.json").bufferedReader().use { JSONObject(it.readText()) }.getJSONObject("database").getJSONArray("entities")
        context.getDatabasePath(name).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            for (i in 0 until schema.length()) {
                val entity = schema.getJSONObject(i); val table = entity.getString("tableName")
                if (table == "sandhya_logs" || (table == "savings" && version == 1)) continue
                var ddl = entity.getString("createSql").replace("\${TABLE_NAME}", table)
                if (table == "recitation_sessions" && version < 3) for (column in listOf("correctness", "completeness", "pronunciation")) ddl = ddl.replace(", `$column` REAL NOT NULL", "")
                db.execSQL(ddl)
            }
            db.version = version
        }
    }
}
