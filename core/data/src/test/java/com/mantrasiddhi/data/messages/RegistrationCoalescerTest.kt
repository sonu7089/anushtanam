package com.mantrasiddhi.data.messages

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RegistrationCoalescerTest {
    private data class Inputs(val uid: String = "a", val token: String = "t", val enabled: Boolean = true, val version: Long = 1)

    @Test fun `concurrent equal requests write once and changed inputs write independently`() = runTest {
        val gate = RegistrationCoalescer<Inputs>()
        var writes = 0
        coroutineScope { repeat(20) { launch { gate.update(Inputs()) { delay(10); writes++ } } } }
        assertEquals(1, writes)
        for (input in listOf(Inputs(token = "new"), Inputs(enabled = false), Inputs(uid = "b"), Inputs(version = 2), Inputs())) {
            gate.update(input) { writes++ }
            gate.update(input) { writes++ }
        }
        assertEquals(6, writes)
    }

    @Test fun `failed registration remains retryable`() = runTest {
        val gate = RegistrationCoalescer<Inputs>()
        runCatching { gate.update(Inputs()) { error("offline") } }
        var writes = 0
        gate.update(Inputs()) { writes++ }
        gate.update(Inputs()) { writes++ }
        assertEquals(1, writes)
    }
}
