package com.mantrasiddhi.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncRunnerTest {
    private val dao = mockk<CloudSyncDao>()
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    private val network = mockk<CloudSyncNetwork>()
    private val transport = mockk<CloudSyncTransport>()
    private val clock = mockk<CloudSyncClock>()
    private val japaSync = mockk<JapaCloudSync>(relaxed = true)
    private val rows = mutableListOf(
        CloudSyncEntity(1, "owner", "device", SyncKind.PROGRESS, "{}", 0),
        CloudSyncEntity(2, "owner", "device", SyncKind.TARGET_INCREMENT, "{}", 0)
    )
    private var nextDue = 60_000L
    private var lastAttempt: Long? = null
    private var timing: String? = null
    private var connected = true
    private var owner: String? = "owner"

    private fun TestScope.runner(): CloudSyncRunner {
        every { auth.currentUser } answers { if (owner == null) null else user }
        every { user.uid } answers { owner!! }
        every { clock.nowMs() } answers { testScheduler.currentTime }
        every { clock.elapsedMs() } answers { testScheduler.currentTime }
        every { clock.bootCount() } returns 1
        coEvery { dao.retryTiming(any()) } answers { timing }
        coEvery { dao.lastAttemptAt(any()) } answers { lastAttempt }
        coEvery { dao.nextAttemptAt(any()) } answers { nextDue }
        coEvery { dao.putRetryTiming(any()) } answers { timing = firstArg<com.mantrasiddhi.data.local.entity.BackupQueueState>().payload }
        coEvery { dao.saveRetryTiming(any(), any(), any(), any()) } answers {
            lastAttempt = secondArg(); nextDue = thirdArg(); timing = arg(3)
        }
        every { network.isConnected() } answers { connected }
        coEvery { dao.hasPending(any()) } answers { rows.any { it.uid == firstArg<String>() } }
        coEvery { dao.pending(any(), any()) } answers { rows.filter { it.uid == firstArg<String>() }.toList() }
        coEvery { dao.acknowledgeBatch(any()) } answers { rows.removeAll { it.id in firstArg<List<Long>>() }; Unit }
        coEvery { transport.deliver(any()) } returns Unit
        return CloudSyncRunner(dao, auth, network, transport, CloudSyncRetryGate(dao, clock), japaSync)
    }

    @Test fun `does not upload before one minute then drains one batch`() = runTest {
        val runner = runner()
        assertTrue(runner.runPending())
        coVerify(exactly = 0) { transport.deliver(any()) }
        advanceTimeBy(60_000)
        assertFalse(runner.runPending())
        coVerify(exactly = 1) { transport.deliver(match { it.size == 2 }) }
        assertTrue(rows.isEmpty())
    }

    @Test fun `manual retry of quarantined records pulls without pending uploads`() = runTest {
        val runner = runner()
        rows.clear()
        var restorePending = false
        coEvery { japaSync.hasQuarantinedRecords("owner") } returns true
        coEvery { japaSync.needsRestore("owner") } answers { restorePending }
        coEvery { japaSync.markRestorePending("owner") } answers { restorePending = true }
        coEvery { japaSync.requestRecovery("owner") } answers { restorePending = true }
        coEvery { japaSync.restore("owner") } answers { restorePending = false }
        assertFalse(runner.syncNow("owner"))
        coVerify(exactly = 1) { japaSync.restore("owner") }
        coVerify(exactly = 0) { transport.deliver(any()) }
        assertTrue(runner.runPending())
        coVerify(exactly = 1) { japaSync.restore("owner") }
    }

    @Test fun `offline work is retained and reconnect obeys the next poll`() = runTest {
        val runner = runner()
        connected = false
        advanceTimeBy(60_000)
        assertTrue(runner.runPending())
        connected = true
        advanceTimeBy(59_999)
        assertTrue(runner.runPending())
        coVerify(exactly = 0) { transport.deliver(any()) }
        advanceTimeBy(1)
        assertFalse(runner.runPending())
    }

    @Test fun `failed upload remains queued and succeeds on the next minute`() = runTest {
        mockkStatic(Log::class)
        try {
            every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
            val runner = runner()
            coEvery { transport.deliver(any()) } throws IOException("offline")
            advanceTimeBy(60_000)
            assertTrue(runner.runPending())
            assertEquals(2, rows.size)
            coVerify(exactly = 0) { dao.acknowledgeBatch(any()) }
            coEvery { transport.deliver(any()) } returns Unit
            advanceTimeBy(60_000)
            assertFalse(runner.runPending())
        } finally { unmockkStatic(Log::class) }
    }

    @Test fun `lost acknowledgement keeps the batch for safe retry`() = runTest {
        val runner = runner()
        coEvery { transport.deliver(any()) } coAnswers { awaitCancellation() }
        advanceTimeBy(60_000)
        assertTrue(runner.runPending())
        assertEquals(80_000L, testScheduler.currentTime)
        assertEquals(2, rows.size)
        coVerify(exactly = 0) { dao.acknowledgeBatch(any()) }
    }

    @Test fun `new runner resumes pending work using the persisted deadline`() = runTest {
        val original = runner()
        advanceTimeBy(30_000)
        assertTrue(original.runPending())
        val restarted = runner()
        advanceTimeBy(30_000)
        assertFalse(restarted.runPending())
    }

    @Test fun `no pending work or another account produces no network access`() = runTest {
        val runner = runner()
        owner = "another-owner"
        advanceTimeBy(60_000)
        assertFalse(runner.runPending())
        owner = null
        assertFalse(runner.runPending())
        coVerify(exactly = 0) { transport.deliver(any()) }
        verify(exactly = 0) { network.isConnected() }
        assertEquals(2, rows.size)
    }

    @Test fun `an interrupted restore retries even after the upload queue is empty`() = runTest {
        val runner = runner()
        rows.clear()
        nextDue = 0
        var restoring = true
        coEvery { japaSync.needsRestore("owner") } answers { restoring }
        coEvery { japaSync.restore("owner") } coAnswers { awaitCancellation() }
        assertTrue(runner.runPending())
        coVerify { japaSync.markFailure("owner") }
        coEvery { japaSync.restore("owner") } coAnswers { restoring = false }
        advanceTimeBy(60_000)
        assertFalse(runner.runPending())
        coVerify(exactly = 0) { transport.deliver(any()) }
    }

    @Test fun `a slow backup keeps completed records acknowledged between retries`() = runTest {
        val runner = runner()
        rows.replaceAll { it.copy(kind = SyncKind.PRACTICE_RECORD) }
        coEvery { transport.deliver(match { it.single().id == 2L }) } coAnswers { awaitCancellation() }
        advanceTimeBy(60_000)
        assertTrue(runner.runPending())
        assertEquals(listOf(2L), rows.map { it.id })
        coEvery { transport.deliver(any()) } returns Unit
        advanceTimeBy(60_000)
        assertFalse(runner.runPending())
        coVerify(exactly = 1) { transport.deliver(match { it.single().id == 1L }) }
    }
    @Test fun `manual sync skips initial wait and repeated taps obey the shared gate`() = runTest {
        val runner = runner()
        assertFalse(runner.syncNow("owner"))
        assertTrue(rows.isEmpty())
        repeat(20) { assertTrue(runner.syncNow("owner")) }
        coVerify(exactly = 1) { transport.deliver(any()) }
        coVerify(exactly = 1) { japaSync.restore("owner") }
    }

    @Test fun `manual retry shares the cooldown after a failed request even with a new runner`() = runTest {
        val runner = runner()
        coEvery { transport.deliver(any()) } coAnswers { awaitCancellation() }
        assertTrue(runner.syncNow("owner"))
        val restarted = runner()
        repeat(10) { assertTrue(restarted.syncNow("owner")) }
        coVerify(exactly = 1) { transport.deliver(any()) }
        advanceTimeBy(60_000)
        coEvery { transport.deliver(any()) } returns Unit
        assertFalse(restarted.syncNow("owner"))
    }

    @Test fun `concurrent manual and automatic triggers never queue duplicate deliveries`() = runTest {
        val runner = runner()
        coEvery { transport.deliver(any()) } coAnswers { awaitCancellation() }
        val first = backgroundScope.launch { runner.syncNow("owner") }
        runCurrent()
        assertEquals("owner", runner.activeUid.value)
        repeat(10) { assertTrue(runner.syncNow("owner")); assertTrue(runner.runPending()) }
        coVerify(exactly = 1) { transport.deliver(any()) }
        first.cancel()
        runCurrent()
        assertNull(runner.activeUid.value)
    }

    @Test fun `a stale settings request cannot sync another account`() = runTest {
        val runner = runner()
        owner = "another-owner"
        assertFalse(runner.syncNow("owner"))
        coVerify(exactly = 0) { japaSync.capture(any()) }
        coVerify(exactly = 0) { transport.deliver(any()) }
    }

    @Test fun `idle manual refresh pulls remote changes without any local event`() = runTest {
        val runner = runner()
        rows.clear()
        var restoring = false
        coEvery { japaSync.requestRecovery("owner") } answers { restoring = true }
        coEvery { japaSync.needsRestore("owner") } answers { restoring }
        coEvery { japaSync.restore("owner") } answers { restoring = false }
        assertFalse(runner.syncNow("owner"))
        advanceTimeBy(60_000)
        assertFalse(runner.syncNow("owner"))
        coVerify(exactly = 2) { japaSync.restore("owner") }
        coVerify(exactly = 0) { transport.deliver(any()) }
    }

    @Test fun `capture failures retain recovery and cannot spin before a claim`() = runTest {
        mockkStatic(Log::class)
        try {
            every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
            val runner = runner()
            coEvery { japaSync.capture("owner") } throws IOException("bad capture")
            advanceTimeBy(60_000)
            assertTrue(runner.runPending())
            repeat(100) { advanceTimeBy(1); assertTrue(runner.runPending()) }
            coVerify(exactly = 1) { japaSync.capture("owner") }
            coVerify(exactly = 1) { japaSync.markFailure("owner") }
            coVerify(exactly = 0) { transport.deliver(any()) }
            advanceTimeBy(59_900)
            coEvery { japaSync.capture("owner") } returns Unit
            assertFalse(runner.runPending())
        } finally { unmockkStatic(Log::class) }
    }

}
