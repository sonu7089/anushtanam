/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import kotlin.math.ln
import kotlin.math.pow

/**
 * A bank of triangular band-pass filters spaced on the perceptual **mel** scale.
 *
 * Mel spacing mimics human pitch perception (fine resolution at low frequencies,
 * coarse at high) and is the standard front-end for MFCC extraction. The bank is
 * built once for a given FFT/sample-rate configuration and reused for every frame.
 *
 * Pure Kotlin so it is JVM-unit-testable.
 *
 * @param filterCount Number of mel filters (typical: 26).
 * @param fftSize FFT length the power spectrum was computed with.
 * @param sampleRate Audio sample rate in Hz.
 * @param lowFreqHz Lowest frequency covered by the bank.
 * @param highFreqHz Highest frequency covered (defaults to Nyquist).
 */
class MelFilterBank(
    private val filterCount: Int,
    private val fftSize: Int,
    private val sampleRate: Int,
    private val lowFreqHz: Double = DEFAULT_LOW_FREQ_HZ,
    private val highFreqHz: Double = sampleRate / 2.0
) {

    private val spectrumBins = fftSize / 2 + 1

    // Precomputed triangular weights: [filterCount][spectrumBins].
    private val filters: Array<DoubleArray> = buildFilters()

    /**
     * Applies the filter bank to a [powerSpectrum] (length `fftSize/2 + 1`),
     * returning one log-energy per mel filter.
     *
     * @return [filterCount] natural-log mel-band energies (floored to avoid log(0)).
     */
    fun logMelEnergies(powerSpectrum: DoubleArray): DoubleArray {
        return DoubleArray(filterCount) { f ->
            var energy = 0.0
            val weights = filters[f]
            val bins = minOf(weights.size, powerSpectrum.size)
            for (bin in 0 until bins) {
                energy += weights[bin] * powerSpectrum[bin]
            }
            ln(energy.coerceAtLeast(ENERGY_FLOOR))
        }
    }

    private fun buildFilters(): Array<DoubleArray> {
        val lowMel = hzToMel(lowFreqHz)
        val highMel = hzToMel(highFreqHz)
        // filterCount + 2 equally-spaced mel points define the triangle edges.
        val melPoints = DoubleArray(filterCount + 2) { i ->
            lowMel + (highMel - lowMel) * i / (filterCount + 1)
        }
        val binFreqs = DoubleArray(filterCount + 2) { i ->
            val hz = melToHz(melPoints[i])
            (hz * fftSize / sampleRate)
        }
        return Array(filterCount) { f ->
            val left = binFreqs[f]
            val center = binFreqs[f + 1]
            val right = binFreqs[f + 2]
            DoubleArray(spectrumBins) { bin ->
                val b = bin.toDouble()
                when {
                    b < left || b > right -> 0.0
                    b <= center -> if (center == left) 0.0 else (b - left) / (center - left)
                    else -> if (right == center) 0.0 else (right - b) / (right - center)
                }
            }
        }
    }

    private fun hzToMel(hz: Double): Double = MEL_FACTOR * log10Plus1(hz / MEL_BREAK_HZ)

    private fun melToHz(mel: Double): Double = MEL_BREAK_HZ * (10.0.pow(mel / MEL_FACTOR) - 1.0)

    private fun log10Plus1(x: Double): Double = kotlin.math.log10(1.0 + x)

    companion object {
        /** Default lower edge of the mel bank (Hz). */
        const val DEFAULT_LOW_FREQ_HZ = 80.0

        /** O'Shaughnessy mel-scale constant. */
        private const val MEL_FACTOR = 2595.0

        /** Mel-scale break frequency (Hz). */
        private const val MEL_BREAK_HZ = 700.0

        /** Minimum band energy before taking the log. */
        private const val ENERGY_FLOOR = 1e-10
    }
}
