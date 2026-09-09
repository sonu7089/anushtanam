package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.data.repository.JapaHistoryRepositoryImpl
import com.mantrasiddhi.domain.usecase.GetJapaSummaryUseCase
import com.mantrasiddhi.data.sync.JapaReplicaMerger
import com.mantrasiddhi.data.sync.payload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class JapaAccountingDatabaseTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        MantraSiddhiDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    private fun db() = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).build()
    private fun draft(period: SandhyaPeriod = SandhyaPeriod.SAYAM, count: Long = 600, revision: Long = 0,
        mode: SandhyaCountMode = SandhyaCountMode.RECORD, date: String = "20260907") =
        SandhyaCountDraft(UUID.randomUUID().toString(), 7, date, period, "Asia/Kolkata", mode, count.toString(), null, revision, 100)
    private suspend fun goals(db: MantraSiddhiDatabase, target: Int = 1000, setBy: Long = 7, mantra: String = "gayatri") {
        db.targetDao().insert(TargetEntity(userId = 7, setByUserId = setBy, targetRecitationCount = target,
            currentRecitationCount = 0, rewardType = "OTHER", rewardDescription = "", isCompleted = false,
            stableId = "target", mantraId = mantra, startedAtMillis = 1))
        db.purashcharanaDao().insert(PurashcharanaEntity(userId = 7, mantraId = mantra, mantraAksharaCount = 24,
            targetJapaCount = 2_400_000, currentJapaCount = 0, startedAtMillis = 1, completedAtMillis = null,
            isActive = true, stableId = "vow"))
    }

    @Test fun credits740OnceAndCorrectionPreservesOvershoot() = runTest {
        val db = db()
        try {
            goals(db, target = 500)
            val dao = db.sandhyaCountDao()
            dao.save(draft(SandhyaPeriod.PRATAH, 108), 10)
            dao.save(draft(SandhyaPeriod.MADHYAHNA, 32), 20)
            val last = draft()
            dao.save(last, 30)
            dao.save(last, 40)
            assertEquals(500, db.japaDao().targets(7).single().currentRecitationCount)
            assertEquals(740L, db.purashcharanaDao().getActiveForUser(7)!!.currentJapaCount)
            assertEquals("740", GetJapaSummaryUseCase(JapaHistoryRepositoryImpl(db))(7).first().lifetime.toString())
            dao.save(draft(count = 500, revision = 1, mode = SandhyaCountMode.EDIT), 50)
            assertEquals(500, db.japaDao().targets(7).single().currentRecitationCount)
            assertEquals(640L, db.purashcharanaDao().getActiveForUser(7)!!.currentJapaCount)
            val corrected = dao.save(draft(count = 300, revision = 2, mode = SandhyaCountMode.EDIT), 60)
            assertTrue(corrected.completionReversed)
            assertEquals(440, db.japaDao().targets(7).single().currentRecitationCount)
            assertTrue(db.recitationDao().getSessionsForUserSync(7).isEmpty())
        } finally { db.close() }
    }

    @Test fun oldCorrectionNeverReplacesNewGoalOrReactivatesWithdrawnVow() = runTest {
        val db = db()
        try {
            goals(db, 500)
            db.sandhyaCountDao().save(draft(), 10)
            db.targetDao().closePrevious(7, 20)
            db.purashcharanaDao().deactivateForUser(7, 20)
            db.targetDao().insert(TargetEntity(userId = 7, setByUserId = 7, targetRecitationCount = 1000,
                currentRecitationCount = 0, rewardType = "OTHER", rewardDescription = "", isCompleted = false,
                stableId = "new-target", startedAtMillis = 20))
            db.sandhyaCountDao().save(draft(count = 400, revision = 1, mode = SandhyaCountMode.EDIT), 30)
            assertEquals("new-target", db.targetDao().getActiveForUser(7)!!.stableId)
            assertEquals(0, db.targetDao().getActiveForUser(7)!!.currentRecitationCount)
            assertEquals(400, db.japaDao().targets(7).first { it.stableId == "target" }.currentRecitationCount)
            assertFalse(db.japaDao().vows(7).single().isActive)
            assertEquals(400L, db.japaDao().vows(7).single().currentJapaCount)
            db.sandhyaCountDao().save(draft(count = 450, revision = 2, mode = SandhyaCountMode.EDIT), 40)
            assertEquals(450, db.japaDao().targets(7).first { it.stableId == "target" }.currentRecitationCount)
        } finally { db.close() }
    }

    @Test fun manualCountsDoNotEarnParentRewardsOrAdvanceOtherMantras() = runTest {
        val db = db()
        try {
            goals(db, setBy = 9)
            db.sandhyaCountDao().save(draft(), 10)
            assertEquals(0, db.japaDao().targets(7).single().currentRecitationCount)
            assertEquals(600L, db.japaDao().vows(7).single().currentJapaCount)
            assertTrue(db.japaDao().entries(8).isEmpty())
        } finally { db.close() }
        val other = db()
        try {
            goals(other, mantra = "other")
            other.sandhyaCountDao().save(draft(), 10)
            assertEquals(0L, other.japaDao().vows(7).single().currentJapaCount)
            assertEquals(0, other.japaDao().targets(7).single().currentRecitationCount)
        } finally { other.close() }
    }

    @Test fun audioAndMentalEntriesUseOneProjectionButPracticeDoesNotCount() = runTest {
        val db = db()
        try {
            goals(db)
            val audio = RecitationSessionEntity(userId = 7, mantraId = "gayatri", mode = "NORMAL",
                overallScore = 0.9, wordScoresJson = "word:0.9", transcription = "word", isCorrect = true,
                durationMs = 1000, createdAt = 100)
            val id = db.recitationDao().insert(audio)
            db.japaDao().recordAudio(id, audio)
            db.japaDao().recordAudio(id, audio)
            db.japaDao().recordAudio(id + 1, audio.copy(mode = "PRACTICE"))
            db.sandhyaCountDao().save(draft(), 200)
            assertEquals(601L, db.japaDao().vows(7).single().currentJapaCount)
            assertEquals("601", GetJapaSummaryUseCase(JapaHistoryRepositoryImpl(db))(7).first().lifetime.toString())
            assertEquals(0.9, db.recitationDao().getStats(7).averageScore, 0.0)
        } finally { db.close() }
    }

    @Test fun migrationKeepsExistingCountsAndEditableHistory() {
        val name = "mf02-migration"
        migrations.createDatabase(name, 11).apply {
            execSQL("INSERT INTO targets VALUES (1, 7, 7, 108, 32, 'OTHER', '', 0)")
            execSQL("INSERT INTO sandhya_counts VALUES (7, '20260907', 'SAYAM', 600, 1, 'Asia/Kolkata')")
            close()
        }
        migrations.runMigrationsAndValidate(name, 12, true, MantraSiddhiDatabase.MIGRATION_11_12).use { db ->
            db.query("SELECT baselineCount, currentRecitationCount, stableId FROM targets").use {
                assertTrue(it.moveToFirst()); assertEquals(32L, it.getLong(0)); assertEquals(32L, it.getLong(1)); assertEquals("legacy-target", it.getString(2))
            }
            db.query("SELECT quantity FROM japa_entries").use { assertTrue(it.moveToFirst()); assertEquals(600L, it.getLong(0)) }
        }
    }

    @Test fun twoOfflineDevicesCombineAndAnOlderSnapshotCannotUndoACorrection() = runTest {
        val first = db()
        val second = db()
        try {
            goals(first); goals(second)
            first.sandhyaCountDao().save(draft(count = 108), 10)
            second.sandhyaCountDao().save(draft(count = 600), 20)
            val server = first.japaDao().entries(7).map { it.payload() } + second.japaDao().entries(7).map { it.payload() }
            JapaReplicaMerger(first).merge(7, emptyList(), server) { true }
            JapaReplicaMerger(second).merge(7, emptyList(), server) { true }
            assertEquals(708L, first.sandhyaCountDao().getCount(7, "20260907", "SAYAM")!!.quantity)
            assertEquals(708L, second.japaDao().vows(7).single().currentJapaCount)
            val revision = second.sandhyaCountDao().getCount(7, "20260907", "SAYAM")!!.revision
            second.sandhyaCountDao().save(draft(count = 608, revision = revision, mode = SandhyaCountMode.EDIT), 30)
            val corrected = second.japaDao().entries(7).map { it.payload() }
            JapaReplicaMerger(first).merge(7, emptyList(), corrected) { true }
            JapaReplicaMerger(first).merge(7, emptyList(), server) { true }
            JapaReplicaMerger(first).merge(7, emptyList(), corrected) { true }
            assertEquals(608L, first.japaDao().vows(7).single().currentJapaCount)
            assertEquals("608", GetJapaSummaryUseCase(JapaHistoryRepositoryImpl(first))(7).first().lifetime.toString())
            assertEquals(2, first.japaDao().entries(7).size)
        } finally { first.close(); second.close() }
    }

    @Test fun restoreRollsBackIfTheAccountChangesOrAPeriodCannotFit() = runTest {
        val db = db()
        try {
            goals(db)
            db.sandhyaCountDao().save(draft(count = Long.MAX_VALUE), 10)
            val remote = db.japaDao().entries(7).single().copy(id = "another-device", quantity = 1).payload()
            assertTrue(runCatching { JapaReplicaMerger(db).merge(7, emptyList(), listOf(remote)) { false } }.isFailure)
            assertTrue(runCatching { JapaReplicaMerger(db).merge(7, emptyList(), listOf(remote)) { true } }.exceptionOrNull() is ArithmeticException)
            assertEquals(1, db.japaDao().entries(7).size)
            assertEquals(Long.MAX_VALUE, db.sandhyaCountDao().getCount(7, "20260907", "SAYAM")!!.quantity)
        } finally { db.close() }
    }

    @Test fun backdatedPracticeDoesNotEnterAGoalTakenLater() = runTest {
        val db = db()
        try {
            goals(db)
            val enrolled = java.time.LocalDate.of(2026, 9, 8).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
            db.japaDao().putTarget(db.japaDao().targets(7).single().copy(startedAtMillis = enrolled))
            db.japaDao().putVow(db.japaDao().vows(7).single().copy(startedAtMillis = enrolled))
            db.sandhyaCountDao().save(draft(), enrolled + 86_400_000)
            assertEquals(0, db.japaDao().targets(7).single().currentRecitationCount)
            assertEquals(0L, db.japaDao().vows(7).single().currentJapaCount)
            assertEquals(600L, db.japaDao().entries(7).single().quantity)
        } finally { db.close() }
    }
}
