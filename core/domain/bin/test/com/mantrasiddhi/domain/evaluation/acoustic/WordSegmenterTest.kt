/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [WordSegmenter]: silence trimming, proportional distribution by benchmark
 * duration, full ordered coverage, and graceful nulls when audio is unusable.
 */
class WordSegmenterTest {

    @Test
    fun `should_returnNulls_when_energiesEmpty`() {
        val segments = WordSegmenter.segment(
            energies = FloatArray(0),
            benchmarkWordDurationsMs = listOf(100.0, 200.0),
            energyThresholdRatio = 0.15f,
            minVoicedFrames = 5
        )
        assertEquals(2, segments.size)
        assertTrue(segments.all { it == null })
    }

    @Test
    fun `should_returnNulls_when_voicedSpanTooShort`() {
        // One loud frame surrounded by silence -> fewer than minVoicedFrames.
        val energies = FloatArray(20) { if (it == 10) 1f else 0f }
        val segments = WordSegmenter.segment(energies, listOf(100.0, 100.0), 0.5f, 5)
        assertTrue(segments.all { it == null })
    }

    @Test
    fun `should_trimSilence_andCoverVoicedSpan`() {
        // Silence [0..9], voiced [10..29], silence [30..39].
        val energies = FloatArray(40) { if (it in 10..29) 1f else 0f }
        val segments = WordSegmenter.segment(energies, listOf(100.0, 100.0), 0.5f, 5)

        assertEquals(2, segments.size)
        assertTrue(segments.all { it != null })
        // First word starts at the voiced onset.
        assertEquals(10, segments[0]!!.first)
        // Last word ends at the voiced offset.
        assertEquals(29, segments[1]!!.last)
        // Two equal-duration words split the 20-frame span roughly in half.
        assertEquals(segments[0]!!.first, 10)
        assertTrue(segments[1]!!.first > segments[0]!!.first)
    }

    @Test
    fun `should_distributeProportionalToBenchmarkDurations`() {
        // One silent frame establishes a noise floor; frames 1..100 are voiced.
        val energies = FloatArray(101) { if (it == 0) 0f else 1f }
        // Word 2 is three times as long as word 1.
        val segments = WordSegmenter.segment(energies, listOf(100.0, 300.0), 0.1f, 5)
        val firstLen = segments[0]!!.last - segments[0]!!.first + 1
        val secondLen = segments[1]!!.last - segments[1]!!.first + 1
        assertTrue(secondLen > firstLen * 2, "longer benchmark word gets a longer span")
    }

    @Test
    fun `should_returnEmptyList_when_noWords`() {
        assertTrue(WordSegmenter.segment(FloatArray(10) { 1f }, emptyList(), 0.1f, 1).isEmpty())
    }
}
