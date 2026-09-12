package com.mantrasiddhi.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mantrasiddhi.data.local.entity.JapaEntryEntity
import com.mantrasiddhi.data.local.entity.PurashcharanaEntity
import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import com.mantrasiddhi.data.local.entity.SandhyaLogEntity
import com.mantrasiddhi.data.local.entity.TargetEntity
import com.mantrasiddhi.data.local.entity.UserProfileEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exercises the production factory on isolated files, including Room's lazy open. */
@RunWith(AndroidJUnit4::class)
class DatabaseInitializationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "initialization-${UUID.randomUUID()}.db"
    private val databases = mutableListOf<MantraSiddhiDatabase>()

    private fun buildDatabase(): MantraSiddhiDatabase =
        MantraSiddhiDatabase.buildDatabase(context, databaseName).also { databases.add(it) }

    @After
    fun closeDatabases() {
        databases.forEach { it.close() }
        context.deleteDatabase(databaseName)
    }

    @Test
    fun freshInstall_startsEmpty_andFirstAccountOwnsIdOne() = runTest {
        val db = buildDatabase()
        assertTrue(db.userDao().getAll().first().isEmpty())
        val profile = realProfile()
        val id = db.userDao().insert(profile)
        assertEquals(1L, id)
        assertEquals(listOf(profile.copy(id = id)), db.userDao().getAll().first())

        db.close()
        val reopened = buildDatabase()
        assertEquals(listOf(profile.copy(id = id)), reopened.userDao().getAll().first())
    }

    @Test
    fun delayedInitialization_cannotReplaceNewAccountOrItsHistory() = runTest {
        // Hold startup access until another caller has created the real ID-1 account.
        // No sleeps: the gate forces the formerly dangerous ordering on every run.
        val delayedDb = buildDatabase()
        val releaseStartup = CompletableDeferred<Unit>()
        val delayedRead = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            releaseStartup.await()
            snapshot(delayedDb, 1L)
        }
        try {
            val signedInDb = buildDatabase()
            val id = signedInDb.userDao().insert(realProfile())
            assertEquals(1L, id)
            addHistory(signedInDb, id)
            val expected = snapshot(signedInDb, id)

            releaseStartup.complete(Unit)
            assertEquals(expected, delayedRead.await())
            assertEquals(expected, snapshot(signedInDb, id))
        } finally {
            delayedRead.cancel()
        }
    }

    @Test
    fun reopeningExistingDatabase_preservesLegacyAndLinkedProfilesWithHistory() = runTest {
        val existing = buildDatabase()
        // Names/IDs matching old demo rows do not prove that a profile is disposable.
        val profiles = listOf(
            UserProfileEntity(1, "Arjun", "INDIVIDUAL", null, 0),
            realProfile().copy(id = 2, name = "Meera", profileType = "PARENT"),
            UserProfileEntity(3, "Aarav", "CHILD", 2, 2),
            UserProfileEntity(4, "Priya", "INDIVIDUAL", null, 3)
        )
        profiles.forEach {
            existing.userDao().insert(it)
            addHistory(existing, it.id)
        }
        val expected = profiles.associate { it.id to snapshot(existing, it.id) }
        existing.close()

        val reopened = buildDatabase()
        assertEquals(profiles.sortedBy { it.id }, reopened.userDao().getAll().first().sortedBy { it.id })
        profiles.forEach { assertEquals(expected[it.id], snapshot(reopened, it.id)) }
        assertEquals(profiles[1], reopened.userDao().getByFirebaseUid("test-account"))
    }

    private fun realProfile() = UserProfileEntity(
        name = "Real account", profileType = "INDIVIDUAL", parentId = null, avatarIndex = 5,
        firebaseUid = "test-account", photoUrl = "https://example.invalid/avatar.png",
        photoSource = "GOOGLE", useGooglePhoto = false
    )

    private suspend fun addHistory(db: MantraSiddhiDatabase, userId: Long) {
        val sessionId = db.recitationDao().insert(RecitationSessionEntity(
            userId = userId, mantraId = "gayatri", mode = "NORMAL", overallScore = 0.95,
            wordScoresJson = "om:0.95", transcription = "om", isCorrect = true,
            durationMs = 3000, createdAt = 1700000000000L
        ))
        db.targetDao().insert(TargetEntity(
            userId = userId, setByUserId = userId, targetRecitationCount = 108,
            currentRecitationCount = 1, rewardType = "OTHER", rewardDescription = "Practice",
            isCompleted = false, stableId = "target-$userId"
        ))
        db.purashcharanaDao().insert(PurashcharanaEntity(
            userId = userId, mantraId = "gayatri", mantraAksharaCount = 24,
            targetJapaCount = 2400000, currentJapaCount = 1, startedAtMillis = 1700000000000L,
            completedAtMillis = null, isActive = true, stableId = "vow-$userId"
        ))
        db.sandhyaLogDao().insertIgnore(SandhyaLogEntity(
            userId = userId, period = "PRATAH", scheduledEpochMillis = 1700000000000L,
            status = "OBSERVED", observedEpochMillis = 1700000000000L, dateKey = "20231114"
        ))
        db.japaDao().putEntry(JapaEntryEntity(
            id = "audio-$userId", userId = userId, mantraId = "gayatri", dateKey = "20231114",
            zoneId = "Asia/Kolkata", period = null, quantity = 1, revision = 1,
            createdAtMillis = 1700000000000L, targetId = "target-$userId", vowId = "vow-$userId",
            source = "AUDIO", sessionId = sessionId
        ))
    }

    private suspend fun snapshot(db: MantraSiddhiDatabase, userId: Long): List<Any?> = listOf(
        db.userDao().getById(userId),
        db.recitationDao().getSessionsForUser(userId).first(),
        db.japaDao().entries(userId),
        db.japaDao().targets(userId),
        db.japaDao().vows(userId),
        db.sandhyaLogDao().getForUserSync(userId)
    )
}
