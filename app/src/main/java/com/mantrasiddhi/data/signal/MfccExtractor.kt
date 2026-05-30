/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import com.mantrasiddhi.domain.evaluation.acoustic.AcousticFeatures
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * On-device Mel-Frequency Cepstral Coefficient (MFCC) extractor.
 *
 * Pipeline: pre-emphasis → framing → Hamming window → power spectrum ([Fft]) →
 * [MelFilterBank] log energies → DCT-II → keep the low-order coefficients →
 * cepstral-mean normalisation (CMN).
 *
 * CMN subtracts each coefficient's per-utterance mean, which cancels the constant
 * channel/mic colouration and so reduces device-to-device variability — important
 * because the learner audio and the benchmark were recorded on different equipment.
 *
 * Pure Kotlin (only [Fft]/[MelFilterBank]); produces the Android-free
 * [AcousticFeatures] the domain layer consumes.
 */
class MfccExtractor @Inject constructor() {

    /**
     * Extracts MFCC frames from mono PCM [samples] sampled at [sampleRate] Hz.
     *
     * Frame/hop sizes are derived from [FRAME_LENGTH_MS]/[HOP_LENGTH_MS] so the
     * configuration is sample-rate independent.
     *
     * @return [AcousticFeatures] (empty when there is not even one full frame).
     */
    fun extract(samples: FloatArray, sampleRate: Int): AcousticFeatures {
        if (sampleRate <= 0) return AcousticFeatures.EMPTY
        val frameLength = (sampleRate * FRAME_LENGTH_MS / MILLIS_PER_SECOND)
        val hopLength = (sampleRate * HOP_LENGTH_MS / MILLIS_PER_SECOND)
        if (frameLength <= 0 || hopLength <= 0 || samples.size < frameLength) {
            return AcousticFeatures.EMPTY
        }

        val emphasised = preEmphasise(samples)
        val window = hammingWindow(frameLength)
        val fftSize = Fft.nextPowerOfTwo(frameLength)
        val filterBank = MelFilterBank(MEL_FILTER_COUNT, fftSize, sampleRate)

        val frameCount = 1 + (emphasised.size - frameLength) / hopLength
        val frames = ArrayList<FloatArray>(frameCount)
        val energies = FloatArray(frameCount)

        val buffer = DoubleArray(frameLength)
        for (f in 0 until frameCount) {
            val offset = f * hopLength
            var energy = 0.0
            for (i in 0 until frameLength) {
                val sample = emphasised[offset + i]
                energy += (sample * sample).toDouble()
                buffer[i] = sample.toDouble() * window[i]
            }
            energies[f] = energy.toFloat()
            val power = Fft.powerSpectrum(buffer)
            val logMel = filterBank.logMelEnergies(power)
            frames.add(dct(logMel))
        }

        cepstralMeanNormalise(frames)
        val frameDurationMs = hopLength.toDouble() * MILLIS_PER_SECOND / sampleRate
        return AcousticFeatures(frames = frames, energies = energies, frameDurationMs = frameDurationMs)
    }

    /** First-order high-pass pre-emphasis to balance the spectrum. */
    private fun preEmphasise(samples: FloatArray): FloatArray {
        val out = FloatArray(samples.size)
        out[0] = samples[0]
        for (i in 1 until samples.size) {
            out[i] = samples[i] - PRE_EMPHASIS * samples[i - 1]
        }
        return out
    }

    private fun hammingWindow(size: Int): DoubleArray = DoubleArray(size) { i ->
        0.54 - 0.46 * cos(2.0 * Math.PI * i / (size - 1))
    }

    /** DCT-II of the [logMel] energies, keeping [MFCC_COEFFICIENTS] coefficients. */
    private fun dct(logMel: DoubleArray): FloatArray {
        val m = logMel.size
        val coeffs = FloatArray(MFCC_COEFFICIENTS)
        val scale = sqrt(2.0 / m)
        for (k in 0 until MFCC_COEFFICIENTS) {
            var sum = 0.0
            for (n in 0 until m) {
                sum += logMel[n] * cos(Math.PI * k * (n + 0.5) / m)
            }
            coeffs[k] = (sum * scale).toFloat()
        }
        return coeffs
    }

    /** Subtracts each coefficient's mean across all [frames] (channel normalisation). */
    private fun cepstralMeanNormalise(frames: List<FloatArray>) {
        if (frames.isEmpty()) return
        val dims = frames[0].size
        val means = DoubleArray(dims)
        for (frame in frames) {
            for (k in 0 until dims) {
                means[k] = means[k] + frame[k].toDouble()
            }
        }
        for (k in 0 until dims) {
            means[k] = means[k] / frames.size
        }
        for (frame in frames) {
            for (k in 0 until dims) {
                frame[k] = (frame[k] - means[k]).toFloat()
            }
        }
    }

    companion object {
        /** Analysis frame length in milliseconds. */
        const val FRAME_LENGTH_MS = 25

        /** Hop (stride) between consecutive frames in milliseconds. */
        const val HOP_LENGTH_MS = 10

        /** Number of mel filters in the bank. */
        const val MEL_FILTER_COUNT = 26

        /** Number of cepstral coefficients kept per frame (includes c0). */
        const val MFCC_COEFFICIENTS = 13

        /** Pre-emphasis coefficient. */
        const val PRE_EMPHASIS = 0.97f

        private const val MILLIS_PER_SECOND = 1000
    }
}
