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
    private var connected = true
    private var owner: String? = "owner"

    private fun TestScope.runner(): CloudSyncRunner {
        every { auth.currentUser } answers { if (owner == null) null else user }
        every { user.uid } answers { owner!! }
        every { clock.nowMs() } answers { testScheduler.currentTime }
        every { network.isConnected() } answers { connected }
        coEvery { dao.hasPending(any()) } answers { rows.any { it.uid == firstArg<String>() } }
        coEvery { dao.pending(any(), any()) } answers { rows.filter { it.uid == firstArg<String>() }.toList() }
        coEvery { dao.claimSyncAttempt(any(), any(), any()) } answers {
            val now = secondArg<Long>()
            if (lastAttempt?.let { now - it < 60_000L } == true || (!thirdArg<Boolean>() && now < nextDue)) false
            else { lastAttempt = now; nextDue = now + 60_000; true }
        }
        coEvery { dao.acknowledgeBatch(any()) } answers { rows.removeAll { it.id in firstArg<List<Long>>() }; Unit }
        coEvery { transport.deliver(any()) } returns Unit
        return CloudSyncRunner(dao, auth, network, transport, clock, japaSync)
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
        advanceTimeBy(15_000)
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
        advanceTimeBy(40_000)
        assertFalse(runner.runPending())
        coVerify(exactly = 1) { transport.deliver(match { it.single().id == 1L }) }
    }
    @Test fun `manual sync skips the initial wait but does nothing once everything is synced`() = runTest {
        val runner = runner()
        assertFalse(runner.syncNow("owner"))
        assertTrue(rows.isEmpty())
        repeat(20) { assertFalse(runner.syncNow("owner")) }
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
        advanceTimeBy(40_000)
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

}
