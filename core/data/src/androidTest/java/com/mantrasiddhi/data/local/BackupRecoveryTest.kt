package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.data.remote.awaitResult
import com.mantrasiddhi.data.repository.JapaHistoryRepositoryImpl
import com.mantrasiddhi.data.sync.*
import com.mantrasiddhi.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class BackupRecoveryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        MantraSiddhiDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun malformedCloudRecordsDoNotPoisonCheckpointsAndRepairBehindCursorIsRetried() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("firebaseEmulator") == "true")
        val host = args.getString("firebaseEmulatorHost") ?: "10.0.2.2"
        require(host in setOf("10.0.2.2", "127.0.0.1"))
        val port = (args.getString("firebaseFirestorePort") ?: "28080").toInt()
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-anushtanam")
            .setApplicationId("1:123456789:android:backup").setApiKey("fake-emulator-key").build(), "invalid-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, (args.getString("firebaseAuthPort") ?: "29099").toInt()) }
        val cloud = FirebaseFirestore.getInstance(app).apply { useEmulator(host, port) }
        val uid = auth.signInAnonymously().awaitResult().user!!.uid
        val db = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).build()
        val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
        val sync = JapaCloudSync(db, cloud, auth, outbox, PracticeBackupStore(db, outbox))
        fun entry(id: String, quantity: Long) = JapaEntryEntity(id, 7, "gayatri", "20260910", "UTC", "SAYAM", quantity, 1, 10, null, null, "SANDHYA").payload()
        fun write(path: String, data: Map<String, Any?>) = emulatorWrite(host, port, "users/$uid/$path", data)
        try {
            db.userDao().insert(UserProfileEntity(7, "Test", "INDIVIDUAL", null, 0, uid))
            // Admin-only emulator seeding reproduces records accepted by older permissive rules.
            val invalid = entry("bad", 24) + ("dateKey" to "not-a-date")
            write("japaEntries/bad", invalid)
            write("backupChanges/entry_bad", mapOf("type" to "entry", "id" to "bad", "value" to invalid,
                "changedAt" to com.google.firebase.Timestamp(100, 0)))
            write("backupChanges/entry_future", mapOf("type" to "future", "id" to "future", "value" to entry("future", 0),
                "changedAt" to com.google.firebase.Timestamp(200, 0)))
            sync.deliver(uid, SyncKind.JAPA_ENTRY, entry("good", 12))
            sync.restore(uid)
            assertEquals(12, db.sandhyaCountDao().getCount(7, "20260910", "SAYAM")!!.quantity)
            assertTrue(db.backupDao().state(uid)!!.needsAttention)
            assertFalse(sync.needsRestore(uid))
            assertTrue(db.backupDao().quarantineCount(uid) >= 2)
            val cursor = db.backupDao().state(uid)!!.cursorSeconds
            assertTrue(cursor > 200)
            // Keep old feed timestamps: repair must not depend on crossing the current cursor.
            write("japaEntries/bad", entry("bad", 24))
            write("backupChanges/entry_bad", mapOf("type" to "entry", "id" to "bad", "value" to entry("bad", 24), "changedAt" to com.google.firebase.Timestamp(100, 0)))
            write("japaEntries/future", entry("future", 0))
            write("backupChanges/entry_future", mapOf("type" to "entry", "id" to "future", "value" to entry("future", 0), "changedAt" to com.google.firebase.Timestamp(200, 0)))
            sync.restore(uid)
            sync.restore(uid)
            assertEquals(36, db.sandhyaCountDao().getCount(7, "20260910", "SAYAM")!!.quantity)
            assertEquals(3, db.japaDao().entries(7).size)
            assertEquals(0, db.backupDao().quarantineCount(uid))
            assertFalse(db.backupDao().state(uid)!!.needsAttention)
            assertEquals(cursor, db.backupDao().state(uid)!!.cursorSeconds)
            // A large invalid set must not trap finalization in an unbounded retry loop.
            repeat(21) { index ->
                val id = "round-${index.toString().padStart(2, '0')}"
                write("backupChanges/entry_$id", mapOf("type" to "future", "id" to id, "value" to entry(id, 0),
                    "changedAt" to com.google.firebase.Timestamp(cursor + 1, 0)))
            }
            sync.restore(uid)
            assertEquals(21, db.backupDao().quarantineCount(uid))
            assertFalse(sync.needsRestore(uid))
            assertEquals("backupChanges/entry_round-19", db.backupDao().queued(uid, "quarantine:retry"))
            write("japaEntries/round-20", entry("round-20", 0))
            write("backupChanges/entry_round-20", mapOf("type" to "entry", "id" to "round-20", "value" to entry("round-20", 0),
                "changedAt" to com.google.firebase.Timestamp(100, 0)))
            sync.restore(uid)
            assertEquals(20, db.backupDao().quarantineCount(uid))
            assertEquals("backupChanges/entry_round-20", db.backupDao().queued(uid, "quarantine:retry"))
        } finally { db.close(); cloud.terminate().awaitResult(); app.delete() }
    }

    /** Deliberate rules bypass is restricted to loopback demo emulators, never live data. */
    private fun emulatorWrite(host: String, port: Int, path: String, data: Map<String, Any?>) {
        require(host in setOf("10.0.2.2", "127.0.0.1"))
        fun value(v: Any?): Map<String, Any?> = when (v) {
            null -> mapOf("nullValue" to null)
            is String -> mapOf("stringValue" to v)
            is Boolean -> mapOf("booleanValue" to v)
            is Number -> mapOf("integerValue" to v.toString())
            is com.google.firebase.Timestamp -> mapOf("timestampValue" to java.time.Instant.ofEpochSecond(v.seconds, v.nanoseconds.toLong()).toString())
            is Map<*, *> -> mapOf("mapValue" to mapOf("fields" to v.entries.associate { it.key.toString() to value(it.value) }))
            else -> error("Unsupported fixture")
        }
        val connection = java.net.URL("http://$host:$port/v1/projects/demo-anushtanam/databases/(default)/documents/$path").openConnection() as java.net.HttpURLConnection
        try {
            connection.requestMethod = "PATCH"; connection.doOutput = true
            connection.connectTimeout = 10000; connection.readTimeout = 10000
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(org.json.JSONObject(mapOf("fields" to data.mapValues { value(it.value) })).toString().toByteArray()) }
            check(connection.responseCode == 200) { "Emulator fixture failed: ${connection.responseCode}" }
        } finally { connection.disconnect() }
    }

    @Test fun migrationKeepsScoresAndPendingWork() {
        val name = "mf08-migration"
        migrations.createDatabase(name, 12).use {
            it.execSQL("INSERT INTO recitation_sessions (id,userId,mantraId,mode,overallScore,wordScoresJson,transcription,isCorrect,durationMs,createdAt,correctness,completeness,pronunciation) VALUES (42,7,'gayatri','PRACTICE',0.8,'om:0.9','om',0,12345,100,0.7,0.8,0.9)")
            it.execSQL("INSERT INTO cloud_sync_outbox VALUES (1,'owner','device','progress','{}',0)")
        }
        migrations.runMigrationsAndValidate(name, 13, true, MantraSiddhiDatabase.MIGRATION_12_13).use {
            it.query("SELECT stableId,wordScoresJson,durationMs FROM recitation_sessions WHERE id=42").use { row ->
                assertTrue(row.moveToFirst()); assertEquals(32, row.getString(0).length)
                assertEquals("om:0.9", row.getString(1)); assertEquals(12345, row.getLong(2))
            }
            it.query("SELECT uid FROM cloud_sync_outbox").use { row -> assertTrue(row.moveToFirst()); assertEquals("owner", row.getString(0)) }
        }
    }

    /** Runs only against local Firebase emulators, never a live project. */
    @Test fun twoReplicasRestorePerformanceCountsAndConflicts() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseEmulator") == "true")
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-anushtanam")
            .setApplicationId("1:123456789:android:backup").setApiKey("fake-emulator-key").build(), "backup-${UUID.randomUUID()}")
        val args = InstrumentationRegistry.getArguments()
        val emulatorHost = args.getString("firebaseEmulatorHost") ?: "10.0.2.2"
        require(emulatorHost in setOf("10.0.2.2", "127.0.0.1"))
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(emulatorHost, (args.getString("firebaseAuthPort") ?: "29099").toInt()) }
        val cloud = FirebaseFirestore.getInstance(app).apply { useEmulator(emulatorHost, (args.getString("firebaseFirestorePort") ?: "28080").toInt()) }
        val uid = auth.signInAnonymously().awaitResult().user!!.uid
        val first = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).build()
        val second = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).build()
        fun sync(db: MantraSiddhiDatabase): JapaCloudSync {
            val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
            return JapaCloudSync(db, cloud, auth, outbox, PracticeBackupStore(db, outbox))
        }
        val a = sync(first); val b = sync(second)
        suspend fun upload(db: MantraSiddhiDatabase, sync: JapaCloudSync) {
            sync.capture(uid)
            while (db.cloudSyncDao().hasPending(uid)) for (row in db.cloudSyncDao().pending(uid)) {
                sync.deliver(uid, row.kind, CloudSyncPolicy.decode(row.payload))
                db.cloudSyncDao().acknowledge(row.id)
            }
        }
        try {
            first.userDao().insert(UserProfileEntity(7, "Meera", "INDIVIDUAL", null, 0, uid))
            second.userDao().insert(UserProfileEntity(17, "Meera", "INDIVIDUAL", null, 0, uid))
            first.targetDao().insert(TargetEntity(userId = 7, setByUserId = 7, targetRecitationCount = 1000,
                currentRecitationCount = 0, rewardType = "OTHER", rewardDescription = "", isCompleted = false,
                stableId = "goal", startedAtMillis = 1))
            val now = System.currentTimeMillis()
            val row = RecitationSessionEntity(userId = 7, mantraId = "gayatri", mode = "NORMAL", overallScore = .9,
                wordScoresJson = "om:0.91", transcription = "om", isCorrect = true, durationMs = 23456, createdAt = now)
            com.mantrasiddhi.data.repository.RecitationRepositoryImpl(first.recitationDao(), first, a)
                .saveSession(row.toDomain())
            // The upload survives immediately, even if the app closes before its observer runs.
            assertTrue(first.cloudSyncDao().pending(uid).any { it.kind == SyncKind.PRACTICE_RECORD })
            val draft = SandhyaCountDraft(UUID.randomUUID().toString(), 7, "20260910", SandhyaPeriod.SAYAM,
                "Asia/Kolkata", SandhyaCountMode.RECORD, "600", null, 0, now)
            first.sandhyaCountDao().save(draft, now)
            first.sandhyaLogDao().insertIgnore(SandhyaLogEntity(userId = 7, period = "PRATAH", scheduledEpochMillis = now,
                status = "OBSERVED", observedEpochMillis = now, dateKey = "20260910"))
            // Cross the 200-record download boundary with complete performance records.
            repeat(201) { first.recitationDao().insert(row.copy(id = 0, mode = "PRACTICE", isCorrect = false, stableId = UUID.randomUUID().toString())) }
            upload(first, a)
            b.restore(uid)
            b.restore(uid)
            assertEquals(202, second.recitationDao().getSessionsForUserSync(17).size)
            assertEquals(23456, second.recitationDao().getSessionsForUserSync(17).first().durationMs)
            assertEquals("om:0.91", second.recitationDao().getSessionsForUserSync(17).first().wordScoresJson)
            assertEquals(601, JapaHistoryRepositoryImpl(second).observe(17).first().sumOf { it.quantity })
            assertEquals(601, second.japaDao().targets(17).single().currentRecitationCount)
            assertNull(second.sandhyaCountDao().getCount(17, "20260910", "PRATAH"))
            assertEquals("OBSERVED", second.sandhyaLogDao().getForUserSync(17).single().status)
            val aCount = first.sandhyaCountDao().getCount(7, "20260910", "SAYAM")!!
            val bCount = second.sandhyaCountDao().getCount(17, "20260910", "SAYAM")!!
            first.sandhyaCountDao().save(draft.copy(id = UUID.randomUUID().toString(), mode = SandhyaCountMode.EDIT,
                input = "540", baseQuantity = 600, baseRevision = aCount.revision), now + 10)
            second.sandhyaCountDao().save(draft.copy(id = UUID.randomUUID().toString(), userId = 17, mode = SandhyaCountMode.EDIT,
                input = "500", baseQuantity = 600, baseRevision = bCount.revision), now + 20)
            upload(first, a); upload(second, b)
            a.restore(uid); b.restore(uid)
            assertEquals(500, first.sandhyaCountDao().getCount(7, "20260910", "SAYAM")!!.quantity)
            val conflict = first.backupDao().observeConflicts(uid).first().single()
            val current = first.japaDao().entry(conflict.entryId)!!
            a.resolveConflict(uid, conflict.id, true, current.operationId)
            upload(first, a); b.restore(uid)
            assertEquals(540, second.sandhyaCountDao().getCount(17, "20260910", "SAYAM")!!.quantity)
            assertEquals(541, JapaHistoryRepositoryImpl(second).observe(17).first().sumOf { it.quantity })
            assertTrue(second.backupDao().observeConflicts(uid).first().isEmpty())
            upload(second, b)
            b.restore(uid)
            b.requestRecovery(uid)
            b.capture(uid)
            assertEquals("Unchanged recovery must leave no uploads", emptyList<String>(),
                second.cloudSyncDao().pending(uid).map { it.kind + ":" + it.payload })
            assertFalse(b.needsRestore(uid))
            // A different account cannot drain or restore the old owner's history.
            auth.signOut(); auth.signInAnonymously().awaitResult()
            assertTrue(runCatching { a.restore(uid) }.isFailure)
        } finally {
            first.close(); second.close(); cloud.terminate().awaitResult(); app.delete()
        }
    }
}
