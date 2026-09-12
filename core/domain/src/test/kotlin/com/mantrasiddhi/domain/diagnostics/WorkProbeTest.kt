package com.mantrasiddhi.domain.diagnostics

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkProbeTest {
    @Test fun `disabled observation returns original producer`() {
        WorkProbe.enabled = false
        val source = flow { emit(1) }
        assertSame(source, WorkProbe.observe("disabled", source))
    }

    @Test fun `replacement and cancellation release active producers`() = runTest {
        WorkProbe.enabled = true
        try {
            val source = WorkProbe.observe("test.replace", flow<Int> { awaitCancellation() })
            val first = launch { source.collect() }
            runCurrent()
            assertEquals(1L, WorkProbe.snapshot().second["test.replace"])
            first.cancelAndJoin()
            val second = launch { source.collect() }
            runCurrent()
            assertEquals(1L, WorkProbe.snapshot().second["test.replace"])
            second.cancelAndJoin()
            assertEquals(0L, WorkProbe.snapshot().second["test.replace"])
            assertEquals(2L, WorkProbe.snapshot().first["subscriptions.test.replace"])
        } finally { WorkProbe.enabled = false }
    }
}
