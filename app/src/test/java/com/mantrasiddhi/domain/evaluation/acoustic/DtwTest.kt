/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [Dtw]: zero distance for identical sequences, symmetry, tempo invariance
 * (a time-stretched copy still aligns at near-zero cost), and larger distance for
 * genuinely different sequences.
 */
class DtwTest {

    private fun frame(vararg v: Float) = v

    @Test
    fun `should_beZero_when_sequencesIdentical`() {
        val seq = listOf(frame(1f, 0f), frame(0f, 1f), frame(1f, 1f))
        assertEquals(0.0, Dtw.distance(seq, seq), 1e-9)
    }

    @Test
    fun `should_beSymmetric`() {
        val a = listOf(frame(1f, 2f), frame(3f, 4f))
        val b = listOf(frame(1f, 2f), frame(0f, 0f), frame(3f, 4f))
        assertEquals(Dtw.distance(a, b), Dtw.distance(b, a), 1e-9)
    }

    @Test
    fun `should_alignAtNearZero_when_oneSequenceTimeStretched`() {
        val base = listOf(frame(1f, 0f), frame(2f, 0f), frame(3f, 0f))
        // Same trajectory, each frame repeated (slower tempo).
        val stretched = listOf(
            frame(1f, 0f), frame(1f, 0f),
            frame(2f, 0f), frame(2f, 0f),
            frame(3f, 0f), frame(3f, 0f)
        )
        assertEquals(0.0, Dtw.distance(base, stretched), 1e-9)
    }

    @Test
    fun `should_beLarger_when_sequencesDiffer`() {
        val a = listOf(frame(1f, 0f), frame(1f, 0f))
        val b = listOf(frame(0f, 5f), frame(0f, 5f))
        assertTrue(Dtw.distance(a, b) > Dtw.distance(a, a))
    }

    @Test
    fun `should_returnZero_when_bothEmpty_andMax_when_oneEmpty`() {
        assertEquals(0.0, Dtw.distance(emptyList(), emptyList()), 1e-9)
        assertEquals(Double.MAX_VALUE, Dtw.distance(listOf(frame(1f)), emptyList()))
    }
}
