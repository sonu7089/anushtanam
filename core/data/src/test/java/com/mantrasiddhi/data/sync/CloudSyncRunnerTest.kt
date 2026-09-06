package com.mantrasiddhi.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
    private val rows = mutableListOf(
        CloudSyncEntity(1, "owner", "device", SyncKind.PROGRESS, "{}", 0),
        CloudSyncEntity(2, "owner", "device", SyncKind.TARGET_INCREMENT, "{}", 0)
    )
    private var nextDue = 60_000L
    private var connected = true
    private var owner: String? = "owner"

    private fun TestScope.runner(): CloudSyncRunner {
        every { auth.currentUser } answers { if (owner == null) null else user }
        every { user.uid } answers { owner!! }
        every { clock.nowMs() } answers { testScheduler.currentTime }
        every { network.isConnected() } answers { connected }
        coEvery { dao.hasPending(any()) } answers { rows.any { it.uid == firstArg<String>() } }
        coEvery { dao.pending(any(), any()) } answers { rows.filter { it.uid == firstArg<String>() }.toList() }
        coEvery { dao.claim(any(), any(), any()) } answers {
            if (secondArg<Long>() < nextDue) 0 else { nextDue = thirdArg(); 1 }
        }
        coEvery { dao.acknowledgeBatch(any()) } answers { rows.removeAll { it.id in firstArg<List<Long>>() }; Unit }
        coEvery { transport.deliver(any()) } returns Unit
        return CloudSyncRunner(dao, auth, network, transport, clock)
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
}
