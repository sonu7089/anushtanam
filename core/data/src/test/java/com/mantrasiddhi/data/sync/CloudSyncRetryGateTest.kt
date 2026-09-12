package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.dao.CloudSyncDao
import com.mantrasiddhi.data.local.entity.BackupQueueState
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CloudSyncRetryGateTest {
    private val dao = mockk<CloudSyncDao>()
    private val clock = mockk<CloudSyncClock>()
    private var wall = 1_000_000L
    private var elapsed = 10_000L
    private var boot = 1L
    private val timing = mutableMapOf<String, String>()
    private val last = mutableMapOf<String, Long>()
    private val next = mutableMapOf<String, Long>()
    private fun gate(): CloudSyncRetryGate {
        every { clock.nowMs() } answers { wall }
        every { clock.elapsedMs() } answers { elapsed }
        every { clock.bootCount() } answers { boot }
        coEvery { dao.retryTiming(any()) } answers { timing[firstArg()] }
        coEvery { dao.lastAttemptAt(any()) } answers { last[firstArg()] }
        coEvery { dao.nextAttemptAt(any()) } answers { next[firstArg()] }
        coEvery { dao.saveRetryTiming(any(), any(), any(), any()) } answers {
            last[firstArg()] = secondArg(); next[firstArg()] = thirdArg(); timing[firstArg()] = arg(3)
        }
        coEvery { dao.putRetryTiming(any()) } answers { firstArg<BackupQueueState>().let { timing[it.uid] = it.payload } }
        return CloudSyncRetryGate(dao, clock)
    }

    @Test fun `wall clock jumps and process restart do not shorten or extend elapsed cooldown`() = runTest {
        val gate = gate()
        assertTrue(gate.claim("a", true))
        wall -= 86_400_000
        elapsed += 20_000
        assertEquals(40_000L, gate.remainingMs("a"))
        assertEquals(40_000L, gate().remainingMs("a"))
        wall += 172_800_000
        assertFalse(gate.claim("a", true))
        elapsed += 40_000
        assertTrue(gate.claim("a", false))
        assertTrue(gate.claim("b", true))
    }

    @Test fun `reboot rebases future wall deadline once and it eventually expires`() = runTest {
        val gate = gate()
        gate.claim("a", true)
        boot++; elapsed = 0; wall -= 86_400_000
        val restarted = gate()
        assertEquals(900_000L, restarted.remainingMs("a"))
        elapsed += 900_000
        assertEquals(0L, restarted.remainingMs("a"))
        assertTrue(restarted.claim("a", false))
    }

    @Test fun `legacy backward clock cooldown cannot be clamped forever`() = runTest {
        val gate = gate()
        last["a"] = wall + 86_400_000
        next["a"] = wall + 86_460_000
        assertEquals(60_000L, gate.remainingMs("a", true))
        elapsed += 60_000
        assertTrue(gate.claim("a", true))
    }

    @Test fun `repeated failures grow to capped jitter and success resets the failure count`() = runTest {
        val gate = gate()
        repeat(12) { index ->
            assertTrue(gate.claim("a", true))
            gate.failed("a")
            val delay = gate.remainingMs("a")
            val ceiling = (60_000L * (1L shl index.coerceAtMost(9))).coerceAtMost(900_000)
            assertTrue(delay in maxOf(60_000, ceiling / 2)..ceiling)
            repeat(10) { assertFalse(gate.claim("a", true)) }
            elapsed += delay; wall += delay
        }
        assertTrue(gate.claim("a", true))
        gate.succeeded("a")
        gate.failed("a")
        assertEquals(60_000L, gate.remainingMs("a"))
    }

    @Test fun `schedule read and write failures have an emergency gate before capture`() = runTest {
        val gate = gate()
        coEvery { dao.retryTiming("a") } throws IllegalStateException("storage")
        try { gate.claim("a", true); fail<Unit>("Expected storage failure") } catch (_: IllegalStateException) {}
        repeat(100) { assertFalse(gate.claim("a", true)) }
        coVerify(exactly = 1) { dao.retryTiming("a") }
        elapsed += 60_000
        coEvery { dao.retryTiming("a") } returns null
        coEvery { dao.saveRetryTiming(any(), any(), any(), any()) } throws IllegalStateException("storage")
        try { gate.claim("a", true); fail<Unit>("Expected storage failure") } catch (_: IllegalStateException) {}
        repeat(100) { assertFalse(gate.claim("a", true)) }
        coVerify(exactly = 1) { dao.saveRetryTiming(any(), any(), any(), any()) }
    }
}
