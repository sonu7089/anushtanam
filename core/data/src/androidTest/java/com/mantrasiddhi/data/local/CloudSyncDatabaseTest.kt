package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import com.mantrasiddhi.data.sync.SyncKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSyncDatabaseTest {
    private class TestClock(context: Context) : com.mantrasiddhi.data.sync.CloudSyncClock(context) {
        var time = 0L
        override fun nowMs() = time
        override fun elapsedMs() = time
        override fun bootCount() = 1L
    }

    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        MantraSiddhiDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    private inline fun <T> MantraSiddhiDatabase.useDb(block: (MantraSiddhiDatabase) -> T): T =
        try { block(this) } finally { close() }

    private fun event(uid: String = "owner", time: Long = 0) =
        CloudSyncEntity(uid = uid, streamId = "device", kind = SyncKind.TARGET_INCREMENT,
            payload = "{}", createdAtMs = time)

    @Test fun newChangesDoNotPostponeFirstMinute_andConcurrentRunnersCannotClaimTwice() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).addCallback(ChangedRecordJournal).build().useDb { db ->
            val dao = db.cloudSyncDao()
            dao.enqueue(event())
            dao.enqueue(event(time = 30_000))
            val clock = TestClock(context)
            val gate = com.mantrasiddhi.data.sync.CloudSyncRetryGate(dao, clock)
            clock.time = 59_999
            assertFalse(gate.claim("owner", false))
            clock.time = 60_000
            assertTrue(gate.claim("owner", false))
            assertFalse(gate.claim("owner", false))
            clock.time = 120_000
            assertTrue(gate.claim("owner", false))
        }
    }

    @Test fun acknowledgingBatchPreservesNewChangesAndOtherAccounts() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).addCallback(ChangedRecordJournal).build().useDb { db ->
            val dao = db.cloudSyncDao()
            dao.enqueue(event())
            dao.enqueue(event("other"))
            val inFlight = dao.pending("owner")
            val newer = dao.enqueue(event(time = 40_000))
            dao.acknowledgeBatch(inFlight.map { it.id })
            assertEquals(listOf(newer), dao.pending("owner").map { it.id })
            assertEquals(1, dao.pending("other").size)
            assertEquals(setOf("owner", "other"), dao.pendingAccounts().first().toSet())
            assertTrue(dao.hasKinds("owner", listOf(SyncKind.TARGET_INCREMENT)))
            assertFalse(dao.hasKinds("owner", listOf(SyncKind.PURASHCHARANA)))
        }
    }

    @Test fun pendingChangesAndTheirDeadlineSurviveDatabaseReopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "cloud-sync-restart-test"
        context.deleteDatabase(name)
        try {
            Room.databaseBuilder(context, MantraSiddhiDatabase::class.java, name).addCallback(ChangedRecordJournal).build().useDb { db ->
                db.cloudSyncDao().enqueue(event())
            }
            Room.databaseBuilder(context, MantraSiddhiDatabase::class.java, name).addCallback(ChangedRecordJournal).build().useDb { db ->
                val dao = db.cloudSyncDao()
                assertEquals("device", dao.pending("owner").single().streamId)
                val clock = TestClock(context)
                val gate = com.mantrasiddhi.data.sync.CloudSyncRetryGate(dao, clock)
                clock.time = 59_999
                assertFalse(gate.claim("owner", false))
                clock.time = 60_000
                assertTrue(gate.claim("owner", false))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun migration8To9PreservesHistoryAndAddsDurableOutbox() {
        val name = "cloud-sync-migration-test"
        migrations.createDatabase(name, 8).apply {
            execSQL("INSERT INTO recitation_sessions (id, userId, mantraId, mode, overallScore, wordScoresJson, transcription, isCorrect, durationMs, createdAt, correctness, completeness, pronunciation) " +
                "VALUES (1, 2, 'gayatri', 'NORMAL', 0.9, 'om:0.9', 'om', 1, 5000, 1700000000000, 0.9, 0.9, 0.9)")
            close()
        }
        migrations.runMigrationsAndValidate(name, 9, true, MantraSiddhiDatabase.MIGRATION_8_9).use { db ->
            db.query("SELECT transcription FROM recitation_sessions WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals("om", it.getString(0))
            }
            db.execSQL("INSERT INTO cloud_sync_outbox (uid, streamId, kind, payload, createdAtMs) VALUES ('owner', 'device', 'target_increment', '{}', 0)")
            db.query("SELECT COUNT(*) FROM cloud_sync_outbox").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test fun manualAndAutomaticAttemptsShareAPersistedAccountCooldown() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "cloud-sync-manual-cooldown"
        context.deleteDatabase(name)
        try {
            Room.databaseBuilder(context, MantraSiddhiDatabase::class.java, name).addCallback(ChangedRecordJournal).build().useDb { db ->
                val dao = db.cloudSyncDao()
                dao.enqueue(event())
                val clock = TestClock(context).apply { time = 1000 }
                val gate = com.mantrasiddhi.data.sync.CloudSyncRetryGate(dao, clock)
                assertFalse(gate.claim("owner", manual = false))
                assertTrue(gate.claim("owner", manual = true))
                assertFalse(gate.claim("owner", manual = true))
            }
            Room.databaseBuilder(context, MantraSiddhiDatabase::class.java, name).addCallback(ChangedRecordJournal).build().useDb { db ->
                val dao = db.cloudSyncDao()
                assertEquals(1000L, dao.lastAttemptAt("owner"))
                val clock = TestClock(context).apply { time = 60_999 }
                val gate = com.mantrasiddhi.data.sync.CloudSyncRetryGate(dao, clock)
                assertFalse(gate.claim("owner", manual = true))
                assertFalse(gate.claim("owner", manual = false))
                clock.time = 61_000
                assertTrue(gate.claim("owner", manual = true))
                assertTrue(gate.claim("another-owner", manual = true))
            }
        } finally { context.deleteDatabase(name) }
    }
}
