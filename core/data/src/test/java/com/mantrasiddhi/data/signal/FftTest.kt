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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Validates [Fft] against an independent naive DFT (the golden reference) and checks
 * the power-spectrum helper concentrates energy at the correct bin.
 */
class FftTest {

    @Test
    fun `should_matchNaiveDft_when_transformingKnownSignal`() {
        val signal = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
        val real = signal.copyOf()
        val imag = DoubleArray(signal.size)
        Fft.transform(real, imag)

        val (refReal, refImag) = naiveDft(signal)
        for (k in signal.indices) {
            assertEquals(refReal[k], real[k], 1e-9, "real bin $k")
            assertEquals(refImag[k], imag[k], 1e-9, "imag bin $k")
        }
    }

    @Test
    fun `should_concentrateEnergyAtBin_when_pureCosine`() {
        val n = 64
        val bin = 8
        val signal = DoubleArray(n) { cos(2.0 * Math.PI * bin * it / n) }
        val power = Fft.powerSpectrum(signal)

        val peak = power.indices.maxByOrNull { power[it] }
        assertEquals(bin, peak, "energy should peak at the cosine's bin")
    }

    @Test
    fun `should_padToPowerOfTwo_when_lengthNotPowerOfTwo`() {
        // 5 samples -> padded to 8 -> spectrum has 8/2 + 1 = 5 bins.
        val power = Fft.powerSpectrum(doubleArrayOf(1.0, 0.0, -1.0, 0.0, 1.0))
        assertEquals(5, power.size)
        assertTrue(power.all { it >= 0.0 })
    }

    @Test
    fun `should_returnNextPowerOfTwo_when_queried`() {
        assertEquals(1, Fft.nextPowerOfTwo(1))
        assertEquals(8, Fft.nextPowerOfTwo(5))
        assertEquals(16, Fft.nextPowerOfTwo(16))
        assertEquals(32, Fft.nextPowerOfTwo(17))
    }

    private fun naiveDft(signal: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val n = signal.size
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (k in 0 until n) {
            for (t in 0 until n) {
                val angle = -2.0 * Math.PI * k * t / n
                re[k] += signal[t] * cos(angle)
                im[k] += signal[t] * sin(angle)
            }
        }
        return re to im
    }
}
