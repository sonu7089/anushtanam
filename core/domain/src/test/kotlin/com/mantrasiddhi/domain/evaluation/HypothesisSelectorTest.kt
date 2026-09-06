/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for [HypothesisSelector]: picks the best-aligning hypothesis (recovering the
 * correct words when they sit below the 1-best), with confidence as a tie-breaker.
 */
class HypothesisSelectorTest {

    @Test
    fun `should_pickBestAligning_when_correctWordsInSecondHypothesis`() {
        val hypotheses = listOf("om bhur wrong" to 0.9f, "om bhur bhuvah" to 0.5f)
        val scores = mapOf("om bhur wrong" to 0.4f, "om bhur bhuvah" to 0.95f)
        val chosen = HypothesisSelector.select(hypotheses) { scores.getValue(it) }
        assertEquals("om bhur bhuvah", chosen?.first)
    }

    @Test
    fun `should_breakTiesByConfidence`() {
        val hypotheses = listOf("a" to 0.4f, "b" to 0.8f)
        val chosen = HypothesisSelector.select(hypotheses) { 0.5f } // equal scores
        assertEquals("b", chosen?.first)
    }

    @Test
    fun `should_returnNull_when_noUsableHypotheses`() {
        assertNull(HypothesisSelector.select(emptyList()) { 1.0f })
        assertNull(HypothesisSelector.select(listOf("" to 0.9f, "   " to 0.5f)) { 1.0f })
    }
}
