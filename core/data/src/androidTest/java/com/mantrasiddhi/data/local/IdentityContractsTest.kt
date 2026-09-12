package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.data.repository.RecitationRepositoryImpl
import com.mantrasiddhi.data.sync.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class IdentityContractsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun db() = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).build()
    private fun session(id: String = "session") = RecitationSessionEntity(userId = 7, mantraId = "gayatri",
        mode = "NORMAL", overallScore = .9, wordScoresJson = "om:0.9", transcription = "om", isCorrect = true,
        durationMs = 1000, createdAt = 100, stableId = id)
    private fun entry(id: String = "entry", session: String? = "session") = JapaEntryEntity(id, 7, "gayatri",
        "19700101", "UTC", null, 1, 1, 100, "target", "vow", "AUDIO_ACCEPTED", sessionStableId = session)
    private fun restorer(db: MantraSiddhiDatabase): BackupRecordRestorer {
        val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
        return BackupRecordRestorer(db, PracticeBackupStore(db, outbox))
    }
    private suspend fun seed(db: MantraSiddhiDatabase, uid: String? = "owner") {
        db.userDao().insert(UserProfileEntity(7, "Owner", "INDIVIDUAL", null, 0, uid))
        db.userDao().insert(UserProfileEntity(8, "Other", "INDIVIDUAL", null, 0, "other"))
    }
    private suspend fun accept(db: MantraSiddhiDatabase, type: String, id: String, payload: Map<String, Any?>): Boolean =
        db.withTransaction { restorer(db).apply("owner", 7, "$type/$id", type, id, payload, { true }) }

    @Test fun immutableAssessmentRetriesAndConcurrentSavesCreditExactlyOnce() = runBlocking {
        val db = db()
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-identity-contracts")
            .setApplicationId("1:123:android:identity").setApiKey("fake-key").build(), UUID.randomUUID().toString())
        try {
            seed(db, uid = null) // Local-only owner: no network or real-account writes.
            val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
            val sync = JapaCloudSync(db, FirebaseFirestore.getInstance(app), FirebaseAuth.getInstance(app), outbox, PracticeBackupStore(db, outbox))
            val repo = RecitationRepositoryImpl(db.recitationDao(), db, sync)
            val assessment = session().toDomain()
            val ids = coroutineScope { List(4) { async { repo.saveSession(assessment) } }.map { it.await() } }
            assertEquals(1, ids.distinct().size)
            val saved = db.backupDao().sessionByLocalId(ids.first())!!
            assertEquals(ids.first(), repo.saveSession(saved.toDomain()))
            for (changed in listOf(assessment.copy(isCorrect = false), assessment.copy(overallScore = .1),
                assessment.copy(mode = com.mantrasiddhi.domain.model.RecitationMode.PRACTICE),
                saved.toDomain().copy(userId = 8), saved.toDomain().copy(mantraId = "other"))) {
                assertTrue(runCatching { repo.saveSession(changed) }.isFailure)
            }
            assertEquals(saved, db.backupDao().sessionByLocalId(saved.id))
            assertEquals(1L, db.japaDao().entries(7).sumOf { it.quantity })
            assertEquals(1, db.recitationDao().getSessionsForUserSync(7).size)
            val failed = assessment.copy(stableId = "failed", isCorrect = false)
            val failedId = repo.saveSession(failed)
            assertTrue(runCatching { repo.saveSession(failed.copy(id = failedId, isCorrect = true)) }.isFailure)
            assertEquals(1, db.japaDao().entries(7).size)
        } finally { db.close(); app.delete() }
    }

    @Test fun entriesSessionsAndGoalsRestoreInEitherOrderAcrossReopen() = runBlocking {
        for (entryFirst in listOf(true, false)) for (legacy in listOf(true, false)) {
            val name = "identity-order-${UUID.randomUUID()}"
            var db = MantraSiddhiDatabase.buildDatabase(context, name)
            try {
                seed(db)
                val practice = PracticeRecordCodec.session(session(), "entry")
                val wireEntry = if (legacy) entry().payload() - "sessionStableId" else entry().payload()
                if (entryFirst) assertTrue(accept(db, "entry", "entry", wireEntry))
                else assertTrue(accept(db, "practice", "session_session", practice))
                db.close(); db = MantraSiddhiDatabase.buildDatabase(context, name)
                if (entryFirst) assertTrue(accept(db, "practice", "session_session", practice))
                else assertTrue(accept(db, "entry", "entry", wireEntry))
                val target = mapOf("id" to "target", "kind" to "target", "mantraId" to "gayatri", "startedAtMillis" to 1L,
                    "endedAtMillis" to null, "baselineCount" to 0L, "targetCount" to 108L, "rewardType" to "OTHER", "rewardDescription" to "")
                val vow = target.filterKeys { it !in setOf("rewardType", "rewardDescription") } +
                    mapOf("id" to "vow", "kind" to "vow", "aksharas" to 24L, "completedAtMillis" to null)
                assertTrue(accept(db, "goal", "target", target))
                assertTrue(accept(db, "goal", "vow", vow))
                assertTrue(accept(db, "entry", "entry", entry().payload()))
                assertTrue(accept(db, "practice", "session_session", practice))
                JapaReplicaMerger(db).merge(7, emptyList(), emptyList()) { true }
                assertNotNull(db.japaDao().entry("entry")!!.sessionId)
                assertEquals("session", db.japaDao().entry("entry")!!.sessionStableId)
                assertEquals("entry", db.backupDao().session(7, "session")!!.acceptedEntryId)
                assertEquals(1, db.japaDao().targets(7).single().currentRecitationCount)
                assertEquals(1L, db.japaDao().vows(7).single().currentJapaCount)
                assertEquals(0, db.backupDao().quarantineCount("owner"))
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun duplicateAssociationsAndContradictoryAssessmentsAreQuarantinedWithoutBlockingPage() = runBlocking {
        val db = db()
        try {
            seed(db)
            assertTrue(accept(db, "entry", "entry", entry().payload()))
            assertFalse(accept(db, "entry", "duplicate", entry("duplicate").payload()))
            assertFalse(accept(db, "practice", "session_session", PracticeRecordCodec.session(session().copy(isCorrect = false), "entry")))
            assertFalse(accept(db, "practice", "session_session", PracticeRecordCodec.session(session().copy(mantraId = "other"), "entry")))
            assertTrue(accept(db, "practice", "session_session", PracticeRecordCodec.session(session(), "entry")))
            assertFalse(accept(db, "practice", "session_session", PracticeRecordCodec.session(session().copy(overallScore = .1), "entry")))
            assertFalse(accept(db, "practice", "session_second", PracticeRecordCodec.session(session("second"), "entry")))
            val other = session("unrelated").copy(mode = "PRACTICE")
            assertTrue(accept(db, "practice", "session_unrelated", PracticeRecordCodec.session(other, null)))
            assertEquals(2, db.recitationDao().getSessionsForUserSync(7).size)
            assertEquals(1, db.japaDao().entries(7).size)
            assertTrue(db.backupDao().quarantineCount("owner") >= 2)
        } finally { db.close() }
    }

    @Test fun sessionFirstConflictingEntryClaimIsRejectedAndOriginalEntryRemainsRestorable() = runBlocking {
        val db = db()
        try {
            seed(db)
            assertTrue(accept(db, "practice", "session_session", PracticeRecordCodec.session(session(), "entry")))
            assertFalse(accept(db, "entry", "wrong", entry("wrong").payload()))
            assertTrue(accept(db, "entry", "entry", entry().payload()))
            assertFalse(accept(db, "entry", "entry", entry().copy(mantraId = "other", revision = 2).payload()))
            assertFalse(accept(db, "entry", "entry", entry().copy(targetId = "changed", revision = 2).payload()))
            assertTrue(db.withTransaction {
                !restorer(db).apply("other", 7, "entry/other", "entry", "other", entry("other", null).payload(), { true })
            })
            assertEquals("target", db.japaDao().entry("entry")!!.targetId)
        } finally { db.close() }
    }

    @Test fun databaseUniquenessRejectsReplacementAndAllowsUnlinkedManualHistory() = runBlocking {
        val db = db()
        try {
            seed(db)
            val row = session()
            val id = db.recitationDao().insert(row)
            assertTrue(runCatching { db.recitationDao().insert(row.copy(overallScore = .1)) }.isFailure)
            assertTrue(runCatching { db.recitationDao().insert(row.copy(id = id, userId = 8)) }.isFailure)
            val target = TargetEntity(userId = 7, setByUserId = 7, targetRecitationCount = 108, currentRecitationCount = 0,
                rewardType = "OTHER", rewardDescription = "", isCompleted = false, stableId = "target")
            db.targetDao().insert(target)
            assertTrue(runCatching { db.targetDao().insert(target.copy(targetRecitationCount = 500)) }.isFailure)
            db.targetDao().insert(target.copy(userId = 8, setByUserId = 8))
            val vow = PurashcharanaEntity(userId = 7, mantraId = "gayatri", mantraAksharaCount = 24, targetJapaCount = 2400000,
                currentJapaCount = 0, startedAtMillis = 1, completedAtMillis = null, isActive = true, stableId = "vow")
            db.purashcharanaDao().insert(vow)
            assertTrue(runCatching { db.purashcharanaDao().insert(vow) }.isFailure)
            val linked = entry().copy(sessionId = id)
            db.japaDao().putEntry(linked)
            assertTrue(runCatching { db.japaDao().putEntry(linked.copy(id = "duplicate")) }.isFailure)
            assertTrue(runCatching { db.japaDao().putEntry(linked.copy(id = "foreign", userId = 8)) }.isFailure)
            assertTrue(runCatching { db.japaDao().putEntry(linked.copy(id = "missing", sessionId = 999)) }.isFailure)
            // Exercise the unique index independently of the DAO's validation guard.
            assertTrue(runCatching { db.openHelper.writableDatabase.execSQL("INSERT INTO japa_entries SELECT 'raw-duplicate',userId,mantraId,dateKey,zoneId,period,quantity,revision,createdAtMillis,targetId,vowId,source,sessionId,updatedAtMillis,operationId,sessionStableId FROM japa_entries WHERE id='entry'") }.isFailure)
            repeat(2) { db.japaDao().putEntry(entry("manual-$it", null).copy(source = "SANDHYA", period = "SAYAM", targetId = null, vowId = null)) }
            assertEquals(3, db.japaDao().entries(7).size)
        } finally { db.close() }
    }
}
