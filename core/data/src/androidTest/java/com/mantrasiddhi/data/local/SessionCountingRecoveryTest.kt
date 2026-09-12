package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.data.remote.awaitResult
import com.mantrasiddhi.data.repository.RecitationRepositoryImpl
import com.mantrasiddhi.data.sync.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Real Android SDK/transactions against local emulators, never a production account. */
class SessionCountingRecoveryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val args get() = InstrumentationRegistry.getArguments()
    private val host get() = args.getString("firebaseEmulatorHost") ?: "127.0.0.1"
    private val port get() = (args.getString("firebaseFirestorePort") ?: "38080").toInt()

    private suspend fun fixture(test: suspend (FirebaseAuth, FirebaseFirestore, String) -> Unit) {
        assumeTrue(args.getString("firebaseEmulator") == "true")
        require(host in setOf("10.0.2.2", "127.0.0.1"))
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-anushtanam")
            .setApplicationId("1:123:android:counting").setApiKey("fake-key").build(), "counting-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, (args.getString("firebaseAuthPort") ?: "39099").toInt()) }
        val cloud = FirebaseFirestore.getInstance(app).apply { useEmulator(host, port) }
        try {
            val uid = auth.signInAnonymously().awaitResult().user!!.uid
            admin("users/$uid", mapOf("uid" to uid, "role" to "CHILD"))
            test(auth, cloud, uid)
        } finally { cloud.terminate().awaitResult(); app.delete() }
    }

    private fun session(id: String, at: Long = 100, correct: Boolean = true, mode: String = "NORMAL") =
        RecitationSessionEntity(userId = 7, mantraId = "gayatri", mode = mode, overallScore = if (correct) .9 else .2,
            wordScoresJson = "", transcription = "om", isCorrect = correct, durationMs = 1000, createdAt = at, stableId = id)

    private fun goal(uid: String, at: Long, target: Long = 1, count: Long = 0) = mapOf(
        "parentUid" to "parent", "childUid" to uid, "rewardType" to "OTHER", "rewardDescription" to "Test",
        "targetRecitationCount" to target, "currentRecitationCount" to count, "isCompleted" to (count == target),
        "createdAt" to Timestamp(java.util.Date(at)))

    @Test fun historicalRepairIsExactVersionedAndReplaysDoNotCreditLaterGoals() = runBlocking {
        fixture { auth, cloud, uid ->
            val records = listOf(session("normal"), session("failed", 200, false), session("practice", 300, true, "PRACTICE"))
            for (row in records) admin("users/$uid/practiceRecords/session_${row.stableId}", PracticeRecordCodec.session(row, null))
            admin("progress/$uid", mapOf("totalSessions" to 99, "correctSessions" to 99, "scoreSum" to 99.0))
            admin("goals/$uid-first", goal(uid, 0, 1, 0))
            admin("goals/$uid-later", goal(uid, 1000))
            val sync = SessionProjectionSync(cloud, auth)
            sync.ensureReady(uid)
            val summary = cloud.collection("progress").document(uid).get(Source.SERVER).awaitResult().data!!
            assertEquals(3L, summary["totalSessions"]); assertEquals(2L, summary["correctSessions"])
            val archive = cloud.collection("users").document(uid).collection("projectionRepairs")
                .document("v2_progress").get(Source.SERVER).awaitResult().data!!
            assertEquals(99L, (archive["value"] as Map<*, *>)["totalSessions"])
            assertEquals(2.0, summary["scoreSum"] as Double, .000001)
            val daily = summary["daily"] as Map<*, *>
            assertEquals(mapOf("n" to 3L, "c" to 2L, "s" to 2.0), daily["1970-01-01"])
            assertEquals(1L, cloud.collection("goals").document("$uid-first").get(Source.SERVER).awaitResult().getLong("currentRecitationCount"))
            for (row in records) sync.deliver(uid, PracticeRecordCodec.session(row, null))
            SessionProjectionSync(cloud, auth).ensureReady(uid) // Process restart: marker prevents rebuilding.
            assertEquals(summary, cloud.collection("progress").document(uid).get(Source.SERVER).awaitResult().data)
            assertEquals(0L, cloud.collection("goals").document("$uid-later").get(Source.SERVER).awaitResult().getLong("currentRecitationCount"))
        }
    }

    @Test fun localCommitThenCloudCommitWithoutAcknowledgementRecoversExactlyOnceAcrossReplicas() = runBlocking {
        fixture { auth, cloud, uid ->
            admin("goals/$uid-first", goal(uid, 0))
            admin("goals/$uid-next", goal(uid, 1, 10))
            val name = "counting-restart-${UUID.randomUUID()}"
            var db = MantraSiddhiDatabase.buildDatabase(context, name)
            fun sync(): JapaCloudSync {
                val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
                return JapaCloudSync(db, cloud, auth, outbox, PracticeBackupStore(db, outbox))
            }
            try {
                db.userDao().insert(UserProfileEntity(7, "Child", "CHILD", null, 0, uid))
                RecitationRepositoryImpl(db.recitationDao(), db, sync()).saveSession(session("saved").toDomain())
                db.close(); db = MantraSiddhiDatabase.buildDatabase(context, name)
                val events = db.cloudSyncDao().pending(uid)
                val transport = CloudSyncTransport(cloud, auth, sync())
                trace("Counting phase: first upload")
                transport.deliver(events)
                // Server committed; simulate death without deleting any Room outbox rows.
                db.close(); db = MantraSiddhiDatabase.buildDatabase(context, name)
                trace("Counting phase: replay after restart")
                CloudSyncTransport(cloud, auth, sync()).deliver(db.cloudSyncDao().pending(uid))
                assertEquals(1L, cloud.collection("progress").document(uid).get(Source.SERVER).awaitResult().getLong("totalSessions"))
                assertEquals(1L, cloud.collection("goals").document("$uid-first").get(Source.SERVER).awaitResult().getLong("currentRecitationCount"))
                assertEquals(0L, cloud.collection("goals").document("$uid-next").get(Source.SERVER).awaitResult().getLong("currentRecitationCount"))
                val second = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).addCallback(ChangedRecordJournal).build()
                try {
                    second.userDao().insert(UserProfileEntity(17, "Child", "CHILD", null, 0, uid))
                    val outbox = CloudSyncOutbox(context, second.cloudSyncDao(), CloudSyncScheduler(context))
                    trace("Counting phase: second replica restore")
                    JapaCloudSync(second, cloud, auth, outbox, PracticeBackupStore(second, outbox)).restore(uid)
                    assertEquals(1, second.recitationDao().getSessionsForUserSync(17).size)
                    assertEquals(1L, second.japaDao().entries(17).sumOf { it.quantity })
                } finally { second.close() }
                val newPayload = PracticeRecordCodec.session(session("competing", 200).copy(projectionDate = "1970-01-01",
                    projectionZoneId = "UTC", childGoalEligible = true), null)
                trace("Counting phase: competing uploads")
                coroutineScope {
                    listOf(async(Dispatchers.IO) { SessionProjectionSync(cloud, auth).deliver(uid, newPayload) },
                        async(Dispatchers.IO) { SessionProjectionSync(cloud, auth).deliver(uid, newPayload) }).awaitAll()
                }
                assertEquals(2L, cloud.collection("progress").document(uid).get(Source.SERVER).awaitResult().getLong("totalSessions"))
                assertEquals(1L, cloud.collection("goals").document("$uid-next").get(Source.SERVER).awaitResult().getLong("currentRecitationCount"))
                coroutineScope {
                    listOf("device-a", "device-b").map { id -> async(Dispatchers.IO) {
                        SessionProjectionSync(cloud, auth).deliver(uid, newPayload + ("id" to id))
                    } }.awaitAll()
                }
                assertEquals(4L, cloud.collection("progress").document(uid).get(Source.SERVER).awaitResult().getLong("totalSessions"))
                assertEquals(3L, cloud.collection("goals").document("$uid-next").get(Source.SERVER).awaitResult().getLong("currentRecitationCount"))
                auth.signOut()
                assertTrue(runCatching { SessionProjectionSync(cloud, auth).deliver(uid, newPayload) }.isFailure)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun repairedMalformedHistoricalSessionCorrectsTheIncompleteProjectionOnce() = runBlocking {
        fixture { auth, cloud, uid ->
            val payload = PracticeRecordCodec.session(session("repairable"), null)
            admin("users/$uid/practiceRecords/session_repairable", payload + ("overallScore" to 2.0))
            admin("goals/$uid-reward", goal(uid, 0))
            val sync = SessionProjectionSync(cloud, auth)
            sync.ensureReady(uid)
            val ref = cloud.collection("progress").document(uid)
            assertEquals(0L, ref.get(Source.SERVER).awaitResult().getLong("totalSessions"))
            assertEquals(1L, ref.get(Source.SERVER).awaitResult().getLong("projectionInvalidRecords"))
            admin("users/$uid/practiceRecords/session_repairable", payload)
            sync.ensureReady(uid)
            val repaired = ref.get(Source.SERVER).awaitResult().data!!
            assertEquals(1L, repaired["totalSessions"]); assertEquals(0L, repaired["projectionInvalidRecords"])
            sync.deliver(uid, payload)
            SessionProjectionSync(cloud, auth).ensureReady(uid)
            assertEquals(repaired, ref.get(Source.SERVER).awaitResult().data)
            assertEquals(1L, cloud.collection("goals").document("$uid-reward").get(Source.SERVER).awaitResult().getLong("currentRecitationCount"))
        }
    }

    private fun trace(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "\n$message\n") })
    }

    private fun admin(path: String, data: Map<String, Any?>) {
        require(host in setOf("10.0.2.2", "127.0.0.1"))
        fun value(v: Any?): Map<String, Any?> = when (v) {
            null -> mapOf("nullValue" to null)
            is String -> mapOf("stringValue" to v)
            is Boolean -> mapOf("booleanValue" to v)
            is Double, is Float -> mapOf("doubleValue" to v)
            is Number -> mapOf("integerValue" to v.toString())
            is Timestamp -> mapOf("timestampValue" to java.time.Instant.ofEpochSecond(v.seconds, v.nanoseconds.toLong()).toString())
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
            check(connection.responseCode == 200) { "Fixture failed: ${connection.responseCode}" }
        } finally { connection.disconnect() }
    }
}
