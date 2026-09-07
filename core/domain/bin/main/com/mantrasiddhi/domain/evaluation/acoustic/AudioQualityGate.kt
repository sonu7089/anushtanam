/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import kotlin.math.log10

/**
 * Decides whether captured audio is good enough to assess pronunciation at all.
 *
 * Penalising a learner for audio we could not hear is worse than not scoring it, so
 * the engine routes low-quality captures to a "not assessed — noisy audio" message
 * instead of a false low pronunciation score (§3.4). Quality is judged from a cheap
 * energy-based SNR estimate combined with the STT confidence.
 */
object AudioQualityGate {

    /** Fraction of frames (by energy) treated as "signal" / "noise" for the estimate. */
    private const val PERCENTILE = 0.2

    /** Floor added to energies to keep the log/ratio finite on digital silence. */
    private const val EPSILON = 1e-9

    /**
     * Estimates the signal-to-noise ratio of [energies], in decibels.
     *
     * Signal is the mean of the loudest [PERCENTILE] of frames, noise the mean of
     * the quietest [PERCENTILE]; their log-ratio approximates SNR without any model.
     *
     * @return SNR in dB, or `0f` when there are no frames.
     */
    fun estimateSnrDb(energies: FloatArray): Float {
        if (energies.isEmpty()) return 0f
        val sorted = energies.sortedDescending()
        val take = (sorted.size * PERCENTILE).toInt().coerceAtLeast(1)
        val signal = sorted.take(take).average() + EPSILON
        val noise = sorted.takeLast(take).average() + EPSILON
        if (noise <= 0.0) return 0f
        return (10.0 * log10(signal / noise)).toFloat()
    }

    /**
     * Returns true when the audio is clear enough to trust acoustically.
     *
     * @param energies Per-frame energy envelope of the capture.
     * @param sttConfidence STT confidence of the chosen transcription (`0.0..1.0`).
     * @param minSnrDb Minimum acceptable SNR in dB.
     * @param minConfidence Minimum acceptable STT confidence.
     * @param minVoicedFrames Minimum number of frames required.
     */
    fun isAssessable(
        energies: FloatArray,
        sttConfidence: Float,
        minSnrDb: Float,
        minConfidence: Float,
        minVoicedFrames: Int
    ): Boolean {
        if (energies.size < minVoicedFrames) return false
        if (sttConfidence < minConfidence) return false
        return estimateSnrDb(energies) >= minSnrDb
    }
}
