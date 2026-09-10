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
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 29099) }
        val cloud = FirebaseFirestore.getInstance(app).apply { useEmulator("10.0.2.2", 28080) }
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
