/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import javax.inject.Inject

/**
 * On-device fundamental-frequency (f0) extractor for the swara/pitch layer (§5.5).
 *
 * Uses short-time **autocorrelation**: for each analysis frame it finds the lag (in
 * the human-voice range [MIN_F0]..[MAX_F0] Hz) with the strongest periodicity. Frames
 * whose normalised peak is below [VOICING_THRESHOLD] are treated as unvoiced (`0`).
 * Autocorrelation is cheap, model-free and robust enough to recover the *shape* of a
 * recitation's pitch accents, which is all the swara comparison needs.
 *
 * Pure Kotlin (no Android) so it can be unit-tested with synthetic tones.
 */
class PitchExtractor @Inject constructor() {

    /**
     * Extracts a per-frame f0 contour (Hz; `0` = unvoiced) from mono PCM [samples].
     *
     * @param samples Mono PCM in `-1.0..1.0`.
     * @param sampleRate Sample rate in Hz.
     * @return f0 per frame in time order, or an empty array when there is not even one
     *   full frame.
     */
    fun extract(samples: FloatArray, sampleRate: Int): FloatArray {
        if (sampleRate <= 0) return FloatArray(0)
        val frameLength = sampleRate * FRAME_MS / MILLIS_PER_SECOND
        val hopLength = sampleRate * HOP_MS / MILLIS_PER_SECOND
        if (frameLength <= 0 || hopLength <= 0 || samples.size < frameLength) return FloatArray(0)

        val minLag = (sampleRate / MAX_F0).coerceAtLeast(1)
        val maxLag = (sampleRate / MIN_F0).coerceAtMost(frameLength - 1)
        if (maxLag <= minLag) return FloatArray(0)

        val frameCount = 1 + (samples.size - frameLength) / hopLength
        val contour = FloatArray(frameCount)
        for (f in 0 until frameCount) {
            contour[f] = frameF0(samples, f * hopLength, frameLength, minLag, maxLag, sampleRate)
        }
        return contour
    }

    private fun frameF0(
        samples: FloatArray,
        offset: Int,
        frameLength: Int,
        minLag: Int,
        maxLag: Int,
        sampleRate: Int
    ): Float {
        var energy = 0.0
        for (i in 0 until frameLength) {
            val s = samples[offset + i]
            energy += s.toDouble() * s
        }
        if (energy < ENERGY_FLOOR) return 0f

        var bestLag = -1
        var bestCorr = 0.0
        for (lag in minLag..maxLag) {
            var corr = 0.0
            var i = 0
            while (i + lag < frameLength) {
                corr += samples[offset + i].toDouble() * samples[offset + i + lag]
                i++
            }
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }
        if (bestLag <= 0) return 0f
        val normalised = bestCorr / energy
        return if (normalised >= VOICING_THRESHOLD) sampleRate.toFloat() / bestLag else 0f
    }

    companion object {
        /** Lowest tracked f0 (covers low adult male voices). */
        const val MIN_F0 = 70

        /** Highest tracked f0 (covers children). */
        const val MAX_F0 = 400

        /** Analysis frame length in milliseconds (long enough for two low-f0 periods). */
        const val FRAME_MS = 50

        /** Hop between frames in milliseconds. */
        const val HOP_MS = 20

        /** Normalised autocorrelation above which a frame is voiced. */
        const val VOICING_THRESHOLD = 0.3

        private const val ENERGY_FLOOR = 1e-6
        private const val MILLIS_PER_SECOND = 1000
    }
}
