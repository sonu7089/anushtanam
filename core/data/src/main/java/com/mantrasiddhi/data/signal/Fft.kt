/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

/**
 * In-place radix-2 Cooley-Tukey Fast Fourier Transform.
 *
 * Pure Kotlin (no Android dependencies) so it can run on-device in milliseconds and
 * be unit-tested against a naive DFT on the JVM. The MFCC pipeline only ever needs
 * power-of-two frames (the analysis window is padded to the next power of two), so a
 * radix-2 implementation is sufficient and fast.
 */
object Fft {

    /**
     * Computes the forward FFT of the complex signal held in [real]/[imag] in place.
     *
     * @param real Real components; length must be a power of two. Overwritten with
     *   the real part of the transform.
     * @param imag Imaginary components; same length as [real]. Overwritten with the
     *   imaginary part of the transform.
     * @throws IllegalArgumentException if the length is not a power of two or the
     *   arrays differ in length.
     */
    fun transform(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(real.size == imag.size) { "real and imag must have equal length" }
        if (n == 0) return
        require(isPowerOfTwo(n)) { "FFT length must be a power of two, was $n" }
        if (n == 1) return

        bitReverseReorder(real, imag)

        var size = 2
        while (size <= n) {
            val half = size / 2
            val angleStep = -2.0 * Math.PI / size
            for (start in 0 until n step size) {
                for (k in 0 until half) {
                    val angle = angleStep * k
                    val wReal = Math.cos(angle)
                    val wImag = Math.sin(angle)
                    val evenIdx = start + k
                    val oddIdx = start + k + half
                    val tReal = wReal * real[oddIdx] - wImag * imag[oddIdx]
                    val tImag = wReal * imag[oddIdx] + wImag * real[oddIdx]
                    real[oddIdx] = real[evenIdx] - tReal
                    imag[oddIdx] = imag[evenIdx] - tImag
                    real[evenIdx] += tReal
                    imag[evenIdx] += tImag
                }
            }
            size *= 2
        }
    }

    /**
     * Returns the power spectrum (|X[k]|^2) of a real [signal] for the first
     * `length/2 + 1` bins (the non-redundant half), zero-padding the signal up to
     * the next power of two.
     */
    fun powerSpectrum(signal: DoubleArray): DoubleArray {
        val n = nextPowerOfTwo(signal.size)
        val real = DoubleArray(n)
        val imag = DoubleArray(n)
        System.arraycopy(signal, 0, real, 0, signal.size)
        transform(real, imag)
        val bins = n / 2 + 1
        return DoubleArray(bins) { k -> real[k] * real[k] + imag[k] * imag[k] }
    }

    /** Smallest power of two `>= value` (and at least 1). */
    fun nextPowerOfTwo(value: Int): Int {
        if (value <= 1) return 1
        var p = 1
        while (p < value) p = p shl 1
        return p
    }

    private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    /** Reorders the arrays by bit-reversed index, the prerequisite for in-place FFT. */
    private fun bitReverseReorder(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
    }
}
