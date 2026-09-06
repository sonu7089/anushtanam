/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tests [PitchExtractor]: that a synthetic tone is tracked to the correct f0,
 * silence is unvoiced, and extraction is deterministic — the properties the swara
 * comparison relies on.
 */
class PitchExtractorTest {

    private val extractor = PitchExtractor()
    private val sampleRate = 16_000

    private fun tone(freq: Double, ms: Int): FloatArray {
        val n = sampleRate * ms / 1000
        return FloatArray(n) { (0.6 * sin(2.0 * PI * freq * it / sampleRate)).toFloat() }
    }

    @Test
    fun `should_trackFundamental_when_pureTone`() {
        val contour = extractor.extract(tone(200.0, 400), sampleRate)
        assertTrue(contour.isNotEmpty())
        val voiced = contour.filter { it > 0f }
        assertTrue(voiced.isNotEmpty(), "a clear tone should be voiced")
        val mean = voiced.average()
        assertEquals(200.0, mean, 15.0, "f0 should be tracked near 200 Hz (was $mean)")
    }

    @Test
    fun `should_reportUnvoiced_when_silence`() {
        val contour = extractor.extract(FloatArray(sampleRate / 2), sampleRate)
        assertTrue(contour.all { it == 0f }, "silence must be entirely unvoiced")
    }

    @Test
    fun `should_returnEmpty_when_shorterThanFrame`() {
        assertTrue(extractor.extract(FloatArray(100), sampleRate).isEmpty())
    }

    @Test
    fun `should_beDeterministic_when_sameInput`() {
        val a = extractor.extract(tone(160.0, 300), sampleRate)
        val b = extractor.extract(tone(160.0, 300), sampleRate)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `should_distinguishPitches_when_tonesDiffer`() {
        val low = extractor.extract(tone(120.0, 300), sampleRate).filter { it > 0f }.average()
        val high = extractor.extract(tone(300.0, 300), sampleRate).filter { it > 0f }.average()
        assertTrue(high > low + 100, "higher tone must track a higher f0 (low=$low high=$high)")
    }
}
