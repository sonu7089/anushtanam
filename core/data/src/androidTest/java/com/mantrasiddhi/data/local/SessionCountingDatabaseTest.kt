package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mantrasiddhi.data.local.entity.UserProfileEntity
import com.mantrasiddhi.data.repository.RecitationRepositoryImpl
import com.mantrasiddhi.data.sync.*
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.model.RecitationMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SessionCountingDatabaseTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun savedSessionAndEveryCountingInputSurviveReopenAndReplay() = runBlocking {
        val name = "counting-${UUID.randomUUID()}"
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-counting")
            .setApplicationId("1:123:android:counting").setApiKey("fake-key").build(), name)
        var db = MantraSiddhiDatabase.buildDatabase(context, name)
        fun sync(): JapaCloudSync {
            val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
            return JapaCloudSync(db, FirebaseFirestore.getInstance(app), FirebaseAuth.getInstance(app), outbox, PracticeBackupStore(db, outbox))
        }
        val session = RecitationSession(userId = 7, mantraId = "gayatri", mode = RecitationMode.NORMAL,
            overallScore = .9, wordScores = emptyMap(), transcription = "om", isCorrect = true,
            durationMs = 1000, createdAt = 1789158600000L, stableId = "stable-operation")
        try {
            db.userDao().insert(UserProfileEntity(7, "Child", "CHILD", null, 0, "original-owner"))
            val repo = RecitationRepositoryImpl(db.recitationDao(), db, sync())
            // Simulates a failure after enqueue but before commit: all rows roll back.
            assertTrue(runCatching { db.withTransaction { repo.saveSession(session); error("crash before commit") } }.isFailure)
            assertEquals(0, db.recitationDao().getSessionsForUserSync(7).size)
            assertFalse(db.cloudSyncDao().hasPending("original-owner"))
            repo.saveSession(session)
            val before = db.cloudSyncDao().pending("original-owner")
            val event = before.single { it.kind == SyncKind.PRACTICE_RECORD }
            val payload = CloudSyncPolicy.decode(event.payload)
            assertEquals("stable-operation", payload["id"])
            assertEquals(true, payload["childGoalEligible"])
            assertEquals(session.createdAt, (payload["createdAt"] as Number).toLong())
            assertNotNull(payload["projectionDate"]); assertNotNull(payload["projectionZoneId"])
            assertFalse(before.any { it.kind == SyncKind.PROGRESS || it.kind == SyncKind.CHILD_GOAL })
            // No ViewModel or post-evaluation callback runs before reopening the file.
            db.close(); db = MantraSiddhiDatabase.buildDatabase(context, name)
            assertEquals(before, db.cloudSyncDao().pending("original-owner"))
            RecitationRepositoryImpl(db.recitationDao(), db, sync()).saveSession(session)
            assertEquals(before, db.cloudSyncDao().pending("original-owner"))
            assertEquals(1L, db.japaDao().entries(7).sumOf { it.quantity })
            for ((id, mode, correct) in listOf(Triple("practice", RecitationMode.PRACTICE, true),
                Triple("failed", RecitationMode.NORMAL, false))) {
                RecitationRepositoryImpl(db.recitationDao(), db, sync()).saveSession(session.copy(stableId = id, mode = mode, isCorrect = correct))
            }
            val payloads = db.cloudSyncDao().pending("original-owner").filter { it.kind == SyncKind.PRACTICE_RECORD }.map { CloudSyncPolicy.decode(it.payload) }
            assertEquals(3, payloads.size)
            assertEquals(1, payloads.count { it["childGoalEligible"] == true })
            assertFalse(db.cloudSyncDao().hasPending("another-owner"))
        } finally { db.close(); context.deleteDatabase(name); app.delete() }
    }
}
