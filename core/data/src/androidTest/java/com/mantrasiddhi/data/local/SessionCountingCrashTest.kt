package com.mantrasiddhi.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.data.repository.RecitationRepositoryImpl
import com.mantrasiddhi.data.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Two explicitly selected processes; never kills the installed application's process. */
class SessionCountingCrashTest {
    @Test fun processDeathAfterCommit() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("countingCrashPhase")
        assumeTrue(phase == "save" || phase == "recover")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "step06-process-death"
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-crash-counting")
            .setApplicationId("1:123:android:crash").setApiKey("fake-key").build(), name)
        val db = MantraSiddhiDatabase.buildDatabase(context, name)
        val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
        val sync = JapaCloudSync(db, FirebaseFirestore.getInstance(app), FirebaseAuth.getInstance(app), outbox, PracticeBackupStore(db, outbox))
        if (phase == "save") {
            db.userDao().insert(UserProfileEntity(7, "Disposable child", "CHILD", null, 0, "crash-owner"))
            val session = RecitationSessionEntity(userId = 7, mantraId = "gayatri", mode = "NORMAL", overallScore = .9,
                wordScoresJson = "", transcription = "om", isCorrect = true, durationMs = 1000, createdAt = 100, stableId = "crash-session")
            RecitationRepositoryImpl(db.recitationDao(), db, sync).saveSession(session.toDomain())
            android.os.Process.killProcess(android.os.Process.myPid())
        } else try {
            val row = db.backupDao().session(7, "crash-session")!!
            val events = db.cloudSyncDao().pending("crash-owner")
            assertEquals(1L, db.japaDao().entries(7).sumOf { it.quantity })
            val payload = CloudSyncPolicy.decode(events.single { it.kind == SyncKind.PRACTICE_RECORD }.payload)
            assertEquals(row.stableId, payload["id"]); assertEquals(true, payload["childGoalEligible"])
            RecitationRepositoryImpl(db.recitationDao(), db, sync).saveSession(row.toDomain())
            assertEquals(events, db.cloudSyncDao().pending("crash-owner"))
        } finally { db.close(); context.deleteDatabase(name); app.delete() }
    }
}
