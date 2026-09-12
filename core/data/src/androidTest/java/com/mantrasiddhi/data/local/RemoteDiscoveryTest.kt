package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.data.remote.awaitResult
import com.mantrasiddhi.data.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Independent Firebase clients and Room replicas on the connected device; demo emulators only. */
class RemoteDiscoveryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private class Replica(val app: FirebaseApp, val auth: FirebaseAuth, val cloud: FirebaseFirestore,
        val db: MantraSiddhiDatabase, val sync: JapaCloudSync, val runner: CloudSyncRunner,
        val discovery: CloudSyncDiscovery, val clock: TestClock)
    private class TestClock(context: Context) : CloudSyncClock(context) {
        var offset = 0L
        override fun nowMs() = super.nowMs() + offset
        override fun elapsedMs() = super.elapsedMs() + offset
    }
    private fun replica(): Replica {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("firebaseEmulatorHost") ?: "127.0.0.1"
        require(host in setOf("127.0.0.1", "10.0.2.2"))
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-anushtanam")
            .setApplicationId("1:123456789:android:discovery").setApiKey("fake-emulator-key").build(), "discovery-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, (args.getString("firebaseAuthPort") ?: "49099").toInt()) }
        val cloud = FirebaseFirestore.getInstance(app).apply { useEmulator(host, (args.getString("firebaseFirestorePort") ?: "48080").toInt()) }
        val db = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).addCallback(ChangedRecordJournal).build()
        val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
        val sync = JapaCloudSync(db, cloud, auth, outbox, PracticeBackupStore(db, outbox))
        val clock = TestClock(context)
        // Emulator traffic uses ADB loopback even when Android cannot validate public Wi-Fi.
        // The fixture above rejects non-loopback hosts; production retains its real network gate.
        val network = object : CloudSyncNetwork(context) { override fun isConnected() = true }
        val runner = CloudSyncRunner(db.cloudSyncDao(), auth, network, CloudSyncTransport(cloud, auth, sync),
            CloudSyncRetryGate(db.cloudSyncDao(), clock), sync)
        return Replica(app, auth, cloud, db, sync, runner, CloudSyncDiscovery(cloud, auth, db, sync), clock)
    }
    private suspend fun Replica.close() { db.close(); cloud.terminate().awaitResult(); app.delete() }
    private suspend fun Replica.profile(uid: String) { db.userDao().insert(UserProfileEntity(7, "Test", "INDIVIDUAL", null, 0, uid)) }
    private fun entry(id: String, count: Long) = JapaEntryEntity(id, 7, "gayatri", "20260910", "UTC", "SAYAM", count, 1, 10,
        null, null, "SANDHYA").payload()
    private suspend fun settled(replica: Replica, uid: String) {
        withTimeout(45_000) {
            do {
                replica.clock.offset += 900_000
                val more = replica.runner.runPending()
            } while (more)
        }
        assertFalse(replica.sync.needsRestore(uid))
        assertFalse(replica.db.cloudSyncDao().hasPending(uid))
    }

    @Test fun idleRemoteChangesOfflineReconnectAndManualPullWithoutLocalPractice() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseEmulator") == "true")
        val a = replica(); val b = replica()
        val email = "discovery-${UUID.randomUUID()}@example.test"
        val password = "Emulator-only-123!"
        val uid = a.auth.createUserWithEmailAndPassword(email, password).awaitResult().user!!.uid
        b.auth.signInWithEmailAndPassword(email, password).awaitResult()
        a.profile(uid); b.profile(uid)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var watcher: Job? = null
        try {
            settled(a, uid); settled(b, uid)
            watcher = scope.launch { b.discovery.watch(uid) }
            withTimeout(10_000) { b.db.backupDao().observeState(uid).first { it?.restorePending == true } }
            settled(b, uid)
            assertTrue(b.db.japaDao().entries(7).isEmpty())
            a.sync.deliver(uid, SyncKind.JAPA_ENTRY, entry("remote-only", 12))
            withTimeout(10_000) { b.db.backupDao().observeState(uid).first { it?.restorePending == true } }
            settled(b, uid)
            assertEquals(12L, b.db.japaDao().entry("remote-only")!!.quantity)
            // A settled receiver stays quiet after the signal is acknowledged.
            delay(300)
            settled(b, uid)
            delay(300)
            assertFalse(b.sync.needsRestore(uid))

            b.cloud.disableNetwork().awaitResult()
            a.sync.deliver(uid, SyncKind.JAPA_ENTRY, entry("offline-arrival", 24))
            delay(300)
            assertNull(b.db.japaDao().entry("offline-arrival"))
            b.cloud.enableNetwork().awaitResult()
            withTimeout(10_000) { b.db.backupDao().observeState(uid).first { it?.restorePending == true } }
            settled(b, uid)
            assertEquals(24L, b.db.japaDao().entry("offline-arrival")!!.quantity)

            // Detached/background receivers can explicitly pull even with no local queue.
            watcher.cancelAndJoin(); watcher = null
            a.sync.deliver(uid, SyncKind.JAPA_ENTRY, entry("manual-only", 36))
            assertFalse(b.sync.needsRestore(uid))
            b.clock.offset += 900_000
            b.runner.syncNow(uid)
            settled(b, uid)
            assertEquals(36L, b.db.japaDao().entry("manual-only")!!.quantity)
            assertEquals(3, b.db.japaDao().entries(7).size)

            // A new refresh request during a pull survives that pull's finalization.
            val inFlight = async(Dispatchers.IO) { b.sync.restore(uid) }
            withTimeout(10_000) { b.db.backupDao().observeState(uid).first { it?.restorePending == true } }
            assertFalse(inFlight.isCompleted)
            b.sync.requestRecovery(uid)
            withTimeout(15_000) { inFlight.await() }
            assertTrue(b.sync.needsRestore(uid))
            settled(b, uid)
        } finally { watcher?.cancelAndJoin(); scope.cancel(); a.close(); b.close() }
    }

    @Test fun accountSwitchRejectsOldSignalsAndDiscoversTheNewAccount() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseEmulator") == "true")
        val a = replica(); val b = replica()
        val password = "Emulator-only-123!"
        val oldEmail = "old-${UUID.randomUUID()}@example.test"
        val oldUid = a.auth.createUserWithEmailAndPassword(oldEmail, password).awaitResult().user!!.uid
        b.auth.signInWithEmailAndPassword(oldEmail, password).awaitResult(); b.profile(oldUid)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var watcher: Job? = null
        try {
            settled(b, oldUid)
            watcher = scope.launch { b.discovery.watch(oldUid) }
            withTimeout(10_000) { b.db.backupDao().observeState(oldUid).first { it?.restorePending == true } }
            settled(b, oldUid)
            val newUid = b.auth.createUserWithEmailAndPassword("new-${UUID.randomUUID()}@example.test", password).awaitResult().user!!.uid
            b.db.userDao().insert(UserProfileEntity(8, "Other", "INDIVIDUAL", null, 0, newUid))
            // Even a queued callback from an old account cannot schedule a restore for it.
            a.sync.deliver(oldUid, SyncKind.JAPA_ENTRY, entry("old-account-change", 48))
            delay(300)
            assertFalse(b.sync.needsRestore(oldUid))
            assertFalse(b.runner.syncNow(oldUid))
            watcher.cancelAndJoin()
            watcher = scope.launch { b.discovery.watch(newUid) }
            withTimeout(10_000) { b.db.backupDao().observeState(newUid).first { it?.restorePending == true } }
            settled(b, newUid)
            assertNull(b.db.japaDao().entry("old-account-change"))
            assertTrue(b.db.japaDao().entries(8).isEmpty())
        } finally { watcher?.cancelAndJoin(); scope.cancel(); a.close(); b.close() }
    }
    internal suspend fun receiveFromOtherDevice(email: String) {
        val b = replica()
        val uid = b.auth.createUserWithEmailAndPassword(email, "Emulator-only-123!").awaitResult().user!!.uid
        b.profile(uid)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var watcher: Job? = null
        try {
            settled(b, uid)
            watcher = scope.launch { b.discovery.watch(uid) }
            withTimeout(10_000) { b.db.backupDao().observeState(uid).first { it?.restorePending == true } }
            settled(b, uid)
            InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                putString("stream", "REMOTE_RECEIVER_READY\n")
            })
            withTimeout(100_000) {
                b.db.backupDao().observeState(uid).first { it?.restorePending == true }
                while (b.db.japaDao().entry("cross-device") == null) {
                    val wait = b.runner.remainingMs(uid)
                    if (wait > 0) delay(wait)
                    b.runner.runPending()
                }
            }
            assertEquals(72L, b.db.japaDao().entry("cross-device")!!.quantity)
            assertEquals(1, b.db.japaDao().entries(7).size)
        } finally { watcher?.cancelAndJoin(); scope.cancel(); b.close() }
    }

    internal suspend fun sendToOtherDevice(email: String) {
        val a = replica()
        try {
            val uid = a.auth.signInWithEmailAndPassword(email, "Emulator-only-123!").awaitResult().user!!.uid
            a.profile(uid)
            a.db.japaDao().putEntry(JapaEntryEntity("cross-device", 7, "gayatri", "20260910", "UTC", "SAYAM",
                72, 1, 10, null, null, "SANDHYA"))
            a.runner.syncNow(uid)
            settled(a, uid)
            assertFalse(a.db.cloudSyncDao().hasPending(uid))
        } finally { a.close() }
    }

}
