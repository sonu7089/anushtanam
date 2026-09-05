/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tests [MfccExtractor]: structural correctness (frame count/dimension, CMN),
 * determinism, and that perceptually different tones yield different cepstra while
 * an identical tone reproduces (the property the DTW comparison relies on).
 */
class MfccExtractorTest {

    private val extractor = MfccExtractor()
    private val sampleRate = 16_000

    private fun tone(freq: Double, ms: Int, amplitude: Double = 0.5): FloatArray {
        val n = sampleRate * ms / 1000
        return FloatArray(n) { (amplitude * sin(2.0 * PI * freq * it / sampleRate)).toFloat() }
    }

    @Test
    fun `should_produceExpectedFrameShape_when_extracting`() {
        val features = extractor.extract(tone(440.0, 200), sampleRate)
        assertFalse(features.isEmpty)
        // 13 cepstral coefficients per frame.
        assertTrue(features.frames.all { it.size == MfccExtractor.MFCC_COEFFICIENTS })
        // 10 ms hop -> 100 frames per second.
        assertEquals(10.0, features.frameDurationMs, 1e-6)
        assertEquals(features.frameCount, features.energies.size)
    }

    @Test
    fun `should_returnEmpty_when_signalShorterThanFrame`() {
        val features = extractor.extract(FloatArray(100), sampleRate)
        assertTrue(features.isEmpty)
    }

    @Test
    fun `should_beDeterministic_when_sameInput`() {
        val a = extractor.extract(tone(440.0, 200), sampleRate)
        val b = extractor.extract(tone(440.0, 200), sampleRate)
        assertEquals(a, b)
    }

    @Test
    fun `should_centreCoefficients_when_cepstralMeanNormalised`() {
        // After CMN each coefficient's mean across frames is ~0.
        val features = extractor.extract(tone(440.0, 300), sampleRate)
        val dims = MfccExtractor.MFCC_COEFFICIENTS
        for (k in 0 until dims) {
            val mean = features.frames.map { it[k].toDouble() }.average()
            assertEquals(0.0, mean, 1e-3, "coefficient $k mean after CMN")
        }
    }

    @Test
    fun `should_differ_when_tonesDifferInPitch`() {
        val low = extractor.extract(tone(220.0, 200), sampleRate)
        val high = extractor.extract(tone(2000.0, 200), sampleRate)
        // Average frame distance between two clearly different tones should be large.
        val frames = minOf(low.frameCount, high.frameCount)
        var diff = 0.0
        for (f in 0 until frames) {
            for (k in 0 until MfccExtractor.MFCC_COEFFICIENTS) {
                diff += kotlin.math.abs(low.frames[f][k] - high.frames[f][k])
            }
        }
        assertTrue(diff > 1.0, "different pitches must yield different cepstra (was $diff)")
    }
}
