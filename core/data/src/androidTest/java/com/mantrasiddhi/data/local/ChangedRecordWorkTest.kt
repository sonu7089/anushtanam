package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.data.sync.*
import com.mantrasiddhi.domain.diagnostics.WorkProbe
import com.mantrasiddhi.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Disposable Room files only; counts actual production queries and encodes on Android. */
class ChangedRecordWorkTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun store(db: MantraSiddhiDatabase): PracticeBackupStore {
        val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
        return PracticeBackupStore(db, outbox)
    }
    private fun entry(id: String, quantity: Long = 10) = JapaEntryEntity(id, 7, "gayatri", "20260912", "UTC",
        "SAYAM", quantity, 1, 100, "target", null, "SANDHYA")
    private fun session(id: String) = RecitationSessionEntity(userId = 7, mantraId = "gayatri", mode = "NORMAL",
        overallScore = .9, wordScoresJson = "", transcription = "", isCorrect = true, durationMs = 100,
        createdAt = 100, stableId = id)
    private suspend fun setup(db: MantraSiddhiDatabase) {
        db.userDao().insert(UserProfileEntity(7, "Fixture", "INDIVIDUAL", null, 0, "test-uid"))
        db.targetDao().insert(TargetEntity(userId = 7, setByUserId = 7, targetRecitationCount = 1000,
            currentRecitationCount = 0, rewardType = "OTHER", rewardDescription = "", isCompleted = false,
            stableId = "target", startedAtMillis = 1))
    }

    @Test fun journalRollsBackAndSurvivesReopenIncludingOlderCorrections() = runBlocking {
        val name = "journal-${UUID.randomUUID()}"
        var db = MantraSiddhiDatabase.buildDatabase(context, name)
        try {
            setup(db)
            store(db).capture(7) { true }
            assertTrue(runCatching {
                db.withTransaction {
                    db.japaDao().putEntry(entry("rollback"))
                    store(db).drain(7)
                    error("rollback")
                }
            }.isFailure)
            assertNull(db.japaDao().entry("rollback"))
            assertFalse(db.backupDao().hasChanges(7))
            db.withTransaction {
                db.japaDao().putEntry(entry("old"))
                db.japaDao().project(7, 100, repair = false)
            }
            db.close()
            db = MantraSiddhiDatabase.buildDatabase(context, name)
            assertTrue(db.backupDao().hasChanges(7))
            store(db).capture(7) { true }
            assertEquals(10, db.japaDao().target(7, "target")!!.currentRecitationCount)
            db.withTransaction {
                db.japaDao().putEntry(entry("old").copy(quantity = 2, revision = 2, operationId = "correction"))
                db.japaDao().project(7, 200, repair = false)
                store(db).drain(7)
            }
            assertEquals(2, db.japaDao().target(7, "target")!!.currentRecitationCount)
            assertFalse(db.backupDao().hasChanges(7))
            val payloads = db.cloudSyncDao().pending("test-uid").filter { it.kind == SyncKind.JAPA_ENTRY }
                .map { CloudSyncPolicy.decode(it.payload) }
            assertTrue(payloads.any { it["operationId"] == "correction" && (it["quantity"] as Number).toLong() == 2L })
            // Unchanged UPSERTs must neither fail on a coalesced key nor dirty the queue.
            db.japaDao().putEntry(db.japaDao().entry("old")!!)
            assertFalse(db.backupDao().hasChanges(7))
            db.purashcharanaDao().insert(PurashcharanaEntity(userId = 7, mantraId = "gayatri", mantraAksharaCount = 24,
                targetJapaCount = 1000, currentJapaCount = 0, startedAtMillis = 1, completedAtMillis = null,
                isActive = true, stableId = "vow"))
            db.japaDao().project(7, 300, repair = false)
            // Remote completion metadata must be reconciled even when quantities did not change.
            db.japaDao().putVow(db.japaDao().vow(7, "vow")!!.copy(completedAtMillis = 400))
            db.japaDao().project(7, 400, repair = false)
            assertNull(db.japaDao().vow(7, "vow")!!.completedAtMillis)
            assertTrue(db.japaDao().projectionIds(7, "project_vow").isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun oneSessionAndUnchangedResumeHaveConstantWorkAcrossHistorySizes() = runBlocking {
        WorkProbe.enabled = true
        try {
            for (size in listOf(0, 1000, 10000)) {
                val name = "journal-work-$size-${UUID.randomUUID()}"
                val db = MantraSiddhiDatabase.buildDatabase(context, name)
                try {
                    setup(db)
                    db.withTransaction { repeat(size) {
                        db.recitationDao().insert(session("history-$it").copy(isCorrect = false))
                        db.japaDao().putEntry(entry("history-entry-$it", 1).copy(targetId = "historical-target"))
                    } }
                    val backup = store(db)
                    backup.capture(7) { true }
                    val before = WorkProbe.snapshot().first
                    db.withTransaction {
                        val row = session("new")
                        val id = db.recitationDao().insert(row)
                        db.japaDao().recordAudio(id, row)
                        backup.drain(7)
                    }
                    val after = WorkProbe.snapshot().first
                    val encodes = after.getOrDefault("backup.encodes", 0) - before.getOrDefault("backup.encodes", 0)
                    assertEquals("size=$size", 2L, encodes)
                    assertEquals(1, db.japaDao().target(7, "target")!!.currentRecitationCount)
                    val settled = WorkProbe.snapshot().first
                    repeat(5) {
                        backup.capture(7) { true }
                        db.japaDao().project(7, 300, repair = false)
                    }
                    val resumed = WorkProbe.snapshot().first
                    for (key in listOf("backup.encodes", "room.UPDATE.targets", "room.SELECT.japa_entries", "room.SELECT.recitation_sessions"))
                        assertEquals("unchanged $key size=$size", settled[key], resumed[key])
                    android.util.Log.i("Step08", "history=$size saveEncodes=$encodes unchangedResumeEncodes=0 goalWrites=0 historyReads=0")
                } finally { db.close(); context.deleteDatabase(name) }
            }
        } finally { WorkProbe.enabled = false }
    }

    @Test fun legacyFingerprintsNormalizeOnceAndUnchangedReminderWritesDoNotQueue() = runBlocking {
        val name = "journal-legacy-${UUID.randomUUID()}"
        val db = MantraSiddhiDatabase.buildDatabase(context, name)
        WorkProbe.enabled = true
        try {
            setup(db)
            val backup = store(db)
            backup.capture(7) { true }
            val payload = mapOf<String, Any?>("id" to "legacy", "quantity" to 1L)
            val key = "${SyncKind.JAPA_ENTRY}::legacy"
            db.backupDao().putQueued(BackupQueueState("test-uid", key, "{\"quantity\":1.0,\"id\":\"legacy\"}"))
            val before = WorkProbe.snapshot().first.getOrDefault("backup.legacyNormalizations", 0)
            db.withTransaction { repeat(3) { backup.enqueue("test-uid", SyncKind.JAPA_ENTRY, payload) } }
            assertEquals(before + 1, WorkProbe.snapshot().first["backup.legacyNormalizations"])
            assertFalse(db.cloudSyncDao().pending("test-uid").any { it.kind == SyncKind.JAPA_ENTRY })
            val row = SandhyaLogEntity(userId = 7, period = "SAYAM", scheduledEpochMillis = 100,
                status = "NOTIFIED", observedEpochMillis = null, dateKey = "20260912")
            db.sandhyaLogDao().insertIgnore(row)
            backup.capture(7) { true }
            db.sandhyaLogDao().markObserved(7, row.dateKey, row.period, 200)
            assertTrue(db.backupDao().hasChanges(7))
            backup.capture(7) { true }
            db.sandhyaLogDao().markObserved(7, row.dateKey, row.period, 200)
            assertFalse(db.backupDao().hasChanges(7))
        } finally { WorkProbe.enabled = false; db.close(); context.deleteDatabase(name) }
    }

    @Test fun boundedPagesPreserveEveryRowAndCorrectionsAcrossPageBoundaries() = runBlocking {
        val name = "journal-pages-${UUID.randomUUID()}"
        val db = MantraSiddhiDatabase.buildDatabase(context, name)
        try {
            setup(db)
            db.withTransaction {
                repeat(450) { i ->
                    val id = "e-${i.toString().padStart(4, '0')}"
                    db.japaDao().putEntry(entry(id, 1))
                    db.backupDao().putChange(SandhyaCountChangeEntity("history-$id", 7, "20260911", "SAYAM", "UTC",
                        "EDIT", i.toLong(), i + 1L, i + 1L, 100))
                }
                db.sandhyaCountDao().rebuildPeriods(7, recordHistory = false)
                db.japaDao().project(7, 100, repair = false)
            }
            val collected = mutableListOf<String>()
            var time = Long.MIN_VALUE
            var after = ""
            do {
                val page = db.japaDao().historyPage(7, time, after)
                assertTrue(page.size <= 200)
                collected += page.map { it.id }
                page.lastOrNull()?.let { time = it.createdAtMillis; after = it.id }
            } while (page.size == 200)
            assertEquals(450, collected.distinct().size)
            assertEquals(450, db.sandhyaCountDao().history(7, "20260911", "SAYAM").size)
            val count = db.sandhyaCountDao().getCount(7, "20260912", "SAYAM")!!
            db.sandhyaCountDao().save(SandhyaCountDraft("correction", 7, "20260912", SandhyaPeriod.SAYAM,
                "UTC", SandhyaCountMode.EDIT, "1", null, count.revision, 200), 200)
            assertEquals(1L, db.sandhyaCountDao().getCount(7, "20260912", "SAYAM")!!.quantity)
            assertEquals(1, db.japaDao().target(7, "target")!!.currentRecitationCount)
            // Verify the shipped SQLite plans on Android, including deterministic ordering.
            for ((sql, index) in listOf(
                "SELECT * FROM targets WHERE userId=7 AND isActive=1 AND isCompleted=0 ORDER BY startedAtMillis DESC,stableId DESC LIMIT 1" to "index_targets_userId_isActive_isCompleted_startedAtMillis_stableId",
                "SELECT * FROM japa_entries WHERE userId=7 AND targetId='target' AND id>'' ORDER BY id LIMIT 200" to "index_japa_entries_userId_targetId_id",
                "SELECT * FROM japa_entries WHERE userId=7 AND vowId='vow' AND id>'' ORDER BY id LIMIT 200" to "index_japa_entries_userId_vowId_id",
                "SELECT * FROM japa_entries WHERE userId=7 ORDER BY createdAtMillis,id LIMIT 200" to "index_japa_entries_userId_createdAtMillis_id"
            )) {
                val plan = db.openHelper.writableDatabase.query("EXPLAIN QUERY PLAN $sql").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }.joinToString()
                }
                assertTrue(plan, plan.contains(index)); assertFalse(plan, plan.contains("TEMP B-TREE"))
                android.util.Log.i("Step08", "plan=$plan")
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun interruptedBootstrapAndAccountLinkRetainAllRecords() = runBlocking {
        val name = "journal-bootstrap-${UUID.randomUUID()}"
        val db = MantraSiddhiDatabase.buildDatabase(context, name)
        try {
            setup(db)
            db.withTransaction { repeat(250) { db.recitationDao().insert(session("old-$it").copy(isCorrect = false)) } }
            var checks = 0
            assertTrue(runCatching { store(db).capture(7) { ++checks < 3 } }.isFailure)
            assertTrue(db.backupDao().hasChanges(7))
            store(db).capture(7) { true }
            assertFalse(db.backupDao().hasChanges(7))
            assertEquals(250L, db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM cloud_sync_outbox WHERE kind='practice_record'").use {
                it.moveToFirst(); it.getLong(0)
            })
            // A different owner receives a distinct bootstrap even after the local queue was drained.
            db.openHelper.writableDatabase.execSQL("UPDATE user_profiles SET firebaseUid='linked-owner' WHERE id=7")
            store(db).capture(7) { true }
            assertEquals("1", db.backupDao().queued("linked-owner", "capture:v1:7"))
            assertEquals(250L, db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM cloud_sync_outbox WHERE uid='linked-owner' AND kind='practice_record'").use {
                it.moveToFirst(); it.getLong(0)
            })
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
