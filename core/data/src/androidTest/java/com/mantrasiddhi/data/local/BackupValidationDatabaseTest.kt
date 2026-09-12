package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.data.sync.*
import com.mantrasiddhi.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class BackupValidationDatabaseTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        MantraSiddhiDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    private fun restorer(db: MantraSiddhiDatabase): BackupRecordRestorer {
        val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
        return BackupRecordRestorer(db, PracticeBackupStore(db, outbox))
    }
    private fun entry(id: String, quantity: Long = 108) = JapaEntryEntity(id, 7, "gayatri", "20260910", "UTC", "SAYAM",
        quantity, 1, 10, null, null, "SANDHYA").payload()
    private suspend fun seed(db: MantraSiddhiDatabase) {
        db.userDao().insert(UserProfileEntity(7, "Owner", "INDIVIDUAL", null, 0, "owner"))
    }
    private suspend fun BackupRecordRestorer.accept(id: String, value: Map<String, Any?>) =
        apply("owner", 7, "japaEntries/$id", "entry", id, value, { true })

    @Test fun pageKeepsValidHistoryAndQuarantineAcrossReopenAndRepair() = runBlocking {
        val name = "backup-validation-${UUID.randomUUID()}"
        var db = MantraSiddhiDatabase.buildDatabase(context, name)
        try {
            seed(db)
            db.withTransaction {
                val restore = restorer(db)
                assertTrue(restore.accept("good", entry("good")))
                assertFalse(restore.accept("bad", entry("bad") + ("dateKey" to "20260230")))
                assertTrue(restore.accept("later", entry("later", 32)))
                db.backupDao().putState(BackupStateEntity("owner", cursorSeconds = 123))
            }
            db.close(); db = MantraSiddhiDatabase.buildDatabase(context, name)
            assertEquals(123, db.backupDao().state("owner")!!.cursorSeconds)
            assertEquals(2, db.japaDao().entries(7).size)
            assertEquals(1, db.backupDao().quarantineCount("owner"))
            assertTrue(db.backupDao().quarantined("owner").single().payload.contains("20260230"))
            db.withTransaction { assertTrue(restorer(db).accept("bad", entry("bad"))) }
            JapaReplicaMerger(db).merge(7, emptyList(), emptyList()) { true }
            assertEquals(248, db.sandhyaCountDao().getCount(7, "20260910", "SAYAM")!!.quantity)
            assertEquals(0, db.backupDao().quarantineCount("owner"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun oversizedMergedPeriodIsRetainedAndRecoversAfterCorrectionWithoutClamping() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).addCallback(ChangedRecordJournal).build()
        try {
            seed(db); val restore = restorer(db)
            db.withTransaction {
                assertTrue(restore.accept("a", entry("a", Long.MAX_VALUE)))
                assertFalse(restore.accept("b", entry("b", Long.MAX_VALUE)))
                assertTrue(restore.accept("other-day", entry("other-day", 32) + ("dateKey" to "20260909")))
            }
            JapaReplicaMerger(db).merge(7, emptyList(), emptyList()) { true }
            assertEquals(Long.MAX_VALUE, db.sandhyaCountDao().getCount(7, "20260910", "SAYAM")!!.quantity)
            assertEquals(1, db.backupDao().quarantineCount("owner"))
            assertTrue(db.backupDao().quarantined("owner").single().payload.contains(Long.MAX_VALUE.toString()))
            db.withTransaction {
                assertTrue(restore.accept("a", entry("a", 0) + mapOf("revision" to 2L, "operationId" to "correct-a")))
                assertTrue(restore.accept("b", entry("b", Long.MAX_VALUE)))
            }
            JapaReplicaMerger(db).merge(7, emptyList(), emptyList()) { true }
            val count = db.sandhyaCountDao().getCount(7, "20260910", "SAYAM")!!
            db.sandhyaCountDao().save(SandhyaCountDraft("edit", 7, "20260910", SandhyaPeriod.SAYAM, "UTC",
                SandhyaCountMode.EDIT, "100", count.quantity, count.revision, 20), 20)
            assertEquals(100, db.japaDao().periodEntries(7, "20260910", "SAYAM").sumOf { it.quantity })
            assertEquals(0, db.backupDao().quarantineCount("owner"))
        } finally { db.close() }
    }

    @Test fun cancelledPageRollsBackQuarantineRowsAndCursorAndDoesNotCatchDatabaseFailures() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).addCallback(ChangedRecordJournal).build()
        try {
            seed(db)
            assertTrue(runCatching { db.withTransaction {
                val restore = restorer(db)
                restore.accept("good", entry("good"))
                restore.accept("bad", entry("bad") + ("zoneId" to "bad/zone"))
                db.backupDao().putState(BackupStateEntity("owner", cursorSeconds = 123))
                restore.apply("owner", 7, "japaEntries/next", "entry", "next", entry("next"), { false })
            } }.isFailure)
            assertTrue(db.japaDao().entries(7).isEmpty())
            assertEquals(0, db.backupDao().quarantineCount("owner"))
            assertNull(db.backupDao().state("owner"))
        } finally { db.close() }
    }

    @Test fun malformedPracticeAndCrossAccountIdentitiesDoNotBlockValidRecords() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).addCallback(ChangedRecordJournal).build()
        try {
            seed(db)
            db.japaDao().putEntry(JapaEntryEntity("taken", 99, "gayatri", "20260910", "UTC", "SAYAM", 12, 1, 10, null, null, "SANDHYA"))
            db.withTransaction {
                val restore = restorer(db)
                assertFalse(restore.accept("taken", entry("taken")))
                val session = PracticeRecordCodec.session(RecitationSessionEntity(userId = 7, mantraId = "gayatri", mode = "NORMAL",
                    overallScore = Double.NaN, wordScoresJson = "", transcription = "", isCorrect = true, durationMs = 1, createdAt = 1, stableId = "s"), null)
                assertFalse(restore.apply("owner", 7, "practiceRecords/session_s", "practice", "session_s", session, { true }))
                assertTrue(restore.accept("good", entry("good")))
            }
            assertEquals(99, db.japaDao().entry("taken")!!.userId)
            assertEquals(2, db.backupDao().quarantineCount("owner"))
            assertEquals(0, db.backupDao().quarantineCount("someone-else"))
            assertEquals(1, db.japaDao().entries(7).size)
        } finally { db.close() }
    }

    @Test fun migrationPreservesHistoryAndOutboxAndSchedulesOneCompatibilityScan() {
        val name = "backup-validation-migration-${UUID.randomUUID()}"
        try {
            migrations.createDatabase(name, 13).use {
                it.execSQL("INSERT INTO backup_state VALUES ('owner',99,0,0,1,123,456)")
                it.execSQL("INSERT INTO backup_queue_state VALUES ('owner','bootstrap:japaEntries','last')")
                it.execSQL("INSERT INTO backup_queue_state VALUES ('owner','practice:session:s','payload')")
                it.execSQL("INSERT INTO cloud_sync_outbox VALUES (1,'owner','device','progress','{}',0)")
            }
            migrations.runMigrationsAndValidate(name, 14, true, MantraSiddhiDatabase.MIGRATION_13_14).use {
                it.query("SELECT lastSuccessMs,restorePending,bootstrapped,cursorSeconds,cursorNanos FROM backup_state").use { row ->
                    assertTrue(row.moveToFirst()); assertEquals(99, row.getLong(0)); assertEquals(1, row.getInt(1)); assertEquals(0, row.getInt(2)); assertEquals(123, row.getLong(3)); assertEquals(456, row.getInt(4))
                }
                it.query("SELECT `key` FROM backup_queue_state").use { row -> assertTrue(row.moveToFirst()); assertEquals("practice:session:s", row.getString(0)); assertFalse(row.moveToNext()) }
                it.query("SELECT uid FROM cloud_sync_outbox").use { row -> assertTrue(row.moveToFirst()); assertEquals("owner", row.getString(0)) }
            }
        } finally { context.deleteDatabase(name) }
    }
}
