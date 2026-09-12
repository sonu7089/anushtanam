package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.local.entity.SandhyaCountDraftEntity
import com.mantrasiddhi.data.local.entity.SandhyaLogEntity
import com.mantrasiddhi.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SandhyaCountDatabaseTest {
    private inline fun <T> MantraSiddhiDatabase.use(block: (MantraSiddhiDatabase) -> T): T =
        try { block(this) } finally { close() }

    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        MantraSiddhiDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun draft(period: SandhyaPeriod = SandhyaPeriod.SAYAM, quantity: Long = 600,
        mode: SandhyaCountMode = SandhyaCountMode.RECORD, base: Long? = null, revision: Long = 0,
        user: Long = 7) = SandhyaCountDraft(UUID.randomUUID().toString(), user, "20260907", period,
        "Asia/Kolkata", mode, quantity.toString(), base, revision, 1)

    @Test fun records740AndCorrectsWithoutDuplicatingOrMarkingObserved() = runTest {
        Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).addCallback(ChangedRecordJournal).build().use { db ->
            val dao = db.sandhyaCountDao()
            dao.save(draft(SandhyaPeriod.PRATAH, 108), 1)
            dao.save(draft(SandhyaPeriod.MADHYAHNA, 32), 2)
            dao.save(draft(quantity = 64), 3)
            val add = draft(quantity = 536, mode = SandhyaCountMode.ADD, base = 64, revision = 1)
            dao.saveDraft(SandhyaCountDraftEntity.from(add))
            val saved = dao.save(add, 4)
            assertEquals(saved, dao.save(add, 5))
            assertNull(dao.getDraft(7))
            dao.saveDraft(SandhyaCountDraftEntity.from(add))
            assertNull(dao.getDraft(7))
            assertEquals(740L, dao.observeCounts(7).first().sumOf { it.quantity })
            assertTrue(db.sandhyaLogDao().getForUserSync(7).isEmpty())
            dao.save(draft(quantity = 500, mode = SandhyaCountMode.EDIT, base = 600, revision = 2), 6)
            assertEquals(640L, dao.observeCounts(7).first().sumOf { it.quantity })
            assertEquals(3, dao.history(7, "20260907", "SAYAM").size)
            assertTrue(dao.observeCounts(8).first().isEmpty())
            assertEquals(600L, dao.history(7, "20260907", "SAYAM").first().previousQuantity)
        }
    }

    @Test fun staleEditAndOverflowRollBackAndKeepDraft() = runTest {
        Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).addCallback(ChangedRecordJournal).build().use { db ->
            val dao = db.sandhyaCountDao()
            dao.save(draft(quantity = Long.MAX_VALUE), 1)
            val overflow = draft(quantity = 1, mode = SandhyaCountMode.ADD, base = Long.MAX_VALUE, revision = 1)
            dao.saveDraft(SandhyaCountDraftEntity.from(overflow))
            assertTrue(runCatching { dao.save(overflow, 2) }.exceptionOrNull() is ArithmeticException)
            assertNotNull(dao.getDraft(7))
            assertNull(dao.getChange(overflow.id))
            assertTrue(runCatching { dao.save(draft(quantity = 100), 3) }.exceptionOrNull() is SandhyaCountConflict)
            assertEquals(Long.MAX_VALUE, dao.getCount(7, "20260907", "SAYAM")!!.quantity)
        }
    }

    @Test fun observedCountStaysUnknownAndZeroIsExplicit() = runTest {
        Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).addCallback(ChangedRecordJournal).build().use { db ->
            db.sandhyaLogDao().insertIgnore(SandhyaLogEntity(userId = 7, dateKey = "20260907",
                period = "SAYAM", scheduledEpochMillis = 1, status = "OBSERVED", observedEpochMillis = 2))
            assertNull(db.sandhyaCountDao().getCount(7, "20260907", "SAYAM"))
            db.sandhyaCountDao().save(draft(quantity = 0), 3)
            assertEquals(0L, db.sandhyaCountDao().getCount(7, "20260907", "SAYAM")!!.quantity)
            assertEquals("OBSERVED", db.sandhyaLogDao().getForUserSync(7).single().status)
        }
    }

    @Test fun draftSurvivesDatabaseReopenAndFinishesOnlyOnce() = runTest {
        val name = "sandhya-draft-${UUID.randomUUID()}"
        val draft = draft(quantity = 57, mode = SandhyaCountMode.COUNTER)
        try {
            Room.databaseBuilder(context, MantraSiddhiDatabase::class.java, name).addCallback(ChangedRecordJournal).build().use {
                it.sandhyaCountDao().saveDraft(SandhyaCountDraftEntity.from(draft))
            }
            Room.databaseBuilder(context, MantraSiddhiDatabase::class.java, name).addCallback(ChangedRecordJournal).build().use {
                val dao = it.sandhyaCountDao()
                val recovered = dao.getDraft(7)!!.toDomain()
                assertEquals(draft, recovered)
                assertNull(dao.getDraft(8))
                assertNull(dao.getCount(7, "20260907", "SAYAM"))
                dao.save(recovered, 1)
                dao.save(recovered, 2)
                assertEquals(57L, dao.getCount(7, "20260907", "SAYAM")!!.quantity)
                dao.save(draft(quantity = 600, mode = SandhyaCountMode.EDIT, base = 57, revision = 1), 3)
                assertEquals(600L, dao.getCount(7, "20260907", "SAYAM")!!.quantity)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun migrationPreservesOldDataWithoutInventingCounts() {
        val name = "sandhya-count-migration"
        migrations.createDatabase(name, 10).apply {
            execSQL("INSERT INTO user_profiles (id, name, profileType, parentId, avatarIndex, firebaseUid, useGooglePhoto) VALUES (7, 'Meera', 'PARENT', NULL, 2, 'owner', 1)")
            execSQL("INSERT INTO targets VALUES (1, 7, 7, 108, 32, 'OTHER', '', 0)")
            execSQL("INSERT INTO sandhya_logs VALUES (1, 7, 'PRATAH', 1, 'OBSERVED', 2, '20260907')")
            execSQL("INSERT INTO cloud_sync_outbox VALUES (1, 'owner', 'device', 'target_increment', '{}', 0)")
            close()
        }
        migrations.runMigrationsAndValidate(name, 11, true, MantraSiddhiDatabase.MIGRATION_10_11).use { db ->
            db.query("SELECT count(*) FROM sandhya_counts").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.query("SELECT observedEpochMillis FROM sandhya_logs").use { assertTrue(it.moveToFirst()); assertEquals(2L, it.getLong(0)) }
            db.query("SELECT currentRecitationCount FROM targets").use { assertTrue(it.moveToFirst()); assertEquals(32L, it.getLong(0)) }
            db.query("SELECT uid FROM cloud_sync_outbox").use { assertTrue(it.moveToFirst()); assertEquals("owner", it.getString(0)) }
        }
    }

    @Test fun everyExportedUpgradePathPreservesObservance() {
        val chain = arrayOf(MantraSiddhiDatabase.MIGRATION_4_5, MantraSiddhiDatabase.MIGRATION_5_6,
            MantraSiddhiDatabase.MIGRATION_6_7, MantraSiddhiDatabase.MIGRATION_7_8,
            MantraSiddhiDatabase.MIGRATION_8_9, MantraSiddhiDatabase.MIGRATION_9_10,
            MantraSiddhiDatabase.MIGRATION_10_11)
        for (version in 4..10) {
            val name = "sandhya-upgrade-$version"
            migrations.createDatabase(name, version).apply {
                execSQL("INSERT INTO user_profiles (id, name, profileType, parentId, avatarIndex) VALUES (7, 'Meera', 'PARENT', NULL, 2)")
                execSQL("INSERT INTO sandhya_logs VALUES (1, 7, 'PRATAH', 1, 'OBSERVED', 2, '20260907')")
                close()
            }
            migrations.runMigrationsAndValidate(name, 11, true, *chain).use { db ->
                db.query("SELECT observedEpochMillis FROM sandhya_logs").use {
                    assertTrue(it.moveToFirst()); assertEquals(2L, it.getLong(0))
                }
                db.query("SELECT count(*) FROM sandhya_counts").use {
                    assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
                }
            }
        }
    }
}
