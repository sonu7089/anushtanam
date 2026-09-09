package com.mantrasiddhi.data.sync

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JapaSyncPolicyTest {
    @Test fun `an unrelated active vow cannot inflate a withdrawn vow baseline`() {
        val goal = mapOf("id" to "legacy-vow-10", "kind" to "vow", "mantraId" to "gayatri",
            "startedAtMillis" to 10L, "targetCount" to 2_400_000L, "baselineCount" to 108L)
        val other = mapOf("mantraId" to "gayatri", "startedAtMillis" to 20L,
            "targetJapaCount" to 2_400_000L, "currentJapaCount" to 600L)
        assertEquals(108L, JapaSyncPolicy.legacyBaseline(goal, other))
    }

    @Test fun `the matching legacy server baseline is preserved once`() {
        val goal = mapOf("id" to "legacy-target", "kind" to "target", "mantraId" to "gayatri",
            "targetCount" to 1000L, "baselineCount" to 108L)
        val mirror = mapOf("targetRecitationCount" to 1000L, "currentRecitationCount" to 600L)
        assertEquals(600L, JapaSyncPolicy.legacyBaseline(goal, mirror))
    }

    @Test fun `a newer target with the same size is not the legacy target`() {
        val goal = mapOf("id" to "legacy-target", "kind" to "target", "mantraId" to "gayatri",
            "targetCount" to 1000L, "baselineCount" to 108L)
        val mirror = mapOf("stableId" to "new-target", "targetRecitationCount" to 1000L, "currentRecitationCount" to 600L)
        assertEquals(108L, JapaSyncPolicy.legacyBaseline(goal, mirror))
    }

    private fun entry(revision: Long, quantity: Long, time: Long = 10, operation: String = "a") =
        mapOf("revision" to revision, "quantity" to quantity, "updatedAtMillis" to time, "operationId" to operation)

    @Test fun `an older 600 snapshot cannot undo a correction to 500`() {
        val original = entry(1, 600, 100)
        val correction = entry(2, 500, 90)
        assertTrue(JapaSyncPolicy.newer(correction, original))
        assertFalse(JapaSyncPolicy.newer(original, correction))
        assertFalse(JapaSyncPolicy.newer(correction, correction))
    }

    @Test fun `concurrent edits converge in either delivery order`() {
        val a = entry(2, 500, operation = "a")
        val b = entry(2, 450, operation = "b")
        fun merge(first: Map<String, Any>, second: Map<String, Any>) = if (JapaSyncPolicy.newer(second, first)) second else first
        assertEquals(b, merge(a, b))
        assertEquals(b, merge(b, a))
    }
}
