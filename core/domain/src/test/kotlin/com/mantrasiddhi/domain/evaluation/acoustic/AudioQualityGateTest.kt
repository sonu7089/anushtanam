/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [AudioQualityGate]: SNR estimation and the assessability decision that keeps
 * the engine from punishing pronunciation on audio it could not hear clearly.
 */
class AudioQualityGateTest {

    @Test
    fun `should_estimateZeroSnr_when_noFrames`() {
        assertTrue(AudioQualityGate.estimateSnrDb(FloatArray(0)) == 0f)
    }

    @Test
    fun `should_estimateHighSnr_when_loudSignalOverQuietNoise`() {
        // Half loud, half near-silent -> clearly positive SNR.
        val energies = FloatArray(40) { if (it < 20) 100f else 0.001f }
        assertTrue(AudioQualityGate.estimateSnrDb(energies) > 20f)
    }

    @Test
    fun `should_estimateLowSnr_when_uniformEnergy`() {
        val energies = FloatArray(40) { 1f }
        assertTrue(AudioQualityGate.estimateSnrDb(energies) < 1f)
    }

    @Test
    fun `should_beAssessable_when_clearAudioAndConfidenceAboveThresholds`() {
        val energies = FloatArray(40) { if (it < 20) 100f else 0.001f }
        val assessable = AudioQualityGate.isAssessable(
            energies = energies,
            sttConfidence = 0.8f,
            minSnrDb = 6f,
            minConfidence = 0.1f,
            minVoicedFrames = 10
        )
        assertTrue(assessable)
    }

    @Test
    fun `should_notBeAssessable_when_tooFewFrames`() {
        val energies = FloatArray(5) { 100f }
        assertFalse(
            AudioQualityGate.isAssessable(energies, 0.9f, 6f, 0.1f, 10)
        )
    }

    @Test
    fun `should_notBeAssessable_when_confidenceTooLow`() {
        val energies = FloatArray(40) { if (it < 20) 100f else 0.001f }
        assertFalse(
            AudioQualityGate.isAssessable(energies, 0.01f, 6f, 0.1f, 10)
        )
    }

    @Test
    fun `should_notBeAssessable_when_snrTooLow`() {
        val energies = FloatArray(40) { 1f } // ~0 dB SNR
        assertFalse(
            AudioQualityGate.isAssessable(energies, 0.9f, 6f, 0.1f, 10)
        )
    }
}
