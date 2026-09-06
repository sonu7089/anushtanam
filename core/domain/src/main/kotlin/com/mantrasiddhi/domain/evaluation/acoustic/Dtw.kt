/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import kotlin.math.sqrt

/**
 * Dynamic Time Warping over two sequences of acoustic feature frames.
 *
 * DTW finds the lowest-cost monotonic alignment between two time series that may
 * differ in length and local tempo, which is exactly the problem of comparing a
 * learner's word against the benchmark word: same phonemes, different timing.
 *
 * Because it compares the learner directly against our **own** benchmark audio it
 * needs no acoustic model, runs in milliseconds on-device, and yields a
 * tempo-invariant pronunciation-similarity-to-reference distance (§3 of the plan).
 *
 * The returned distance is normalised by the warping-path length so sequences of
 * different lengths are comparable.
 */
object Dtw {

    /** Large sentinel used to seed the DP table's forbidden cells. */
    private const val INF = Double.MAX_VALUE / 4

    /**
     * Computes the length-normalised DTW distance between feature sequences [a] and
     * [b] using Euclidean per-frame distance.
     *
     * @return Average aligned per-frame Euclidean distance (`0.0` for identical
     *   sequences). Returns [Double.MAX_VALUE] when one sequence is empty and the
     *   other is not (maximally dissimilar), and `0.0` when both are empty.
     */
    fun distance(a: List<FloatArray>, b: List<FloatArray>): Double {
        if (a.isEmpty() && b.isEmpty()) return 0.0
        if (a.isEmpty() || b.isEmpty()) return Double.MAX_VALUE
        val n = a.size
        val m = b.size

        // cost[j] = accumulated cost; steps[j] = number of frames on the best path
        // reaching this cell, so we can length-normalise the final distance.
        var prevCost = DoubleArray(m + 1) { INF }
        var prevSteps = IntArray(m + 1)
        var curCost = DoubleArray(m + 1)
        var curSteps = IntArray(m + 1)
        prevCost[0] = 0.0

        for (i in 1..n) {
            curCost[0] = INF
            curSteps[0] = 0
            for (j in 1..m) {
                val local = euclidean(a[i - 1], b[j - 1])
                // Pick the cheapest predecessor (match / insertion / deletion).
                val best = minOf(prevCost[j - 1], prevCost[j], curCost[j - 1])
                val steps: Int
                val base: Double
                when (best) {
                    prevCost[j - 1] -> { base = prevCost[j - 1]; steps = prevSteps[j - 1] }
                    prevCost[j] -> { base = prevCost[j]; steps = prevSteps[j] }
                    else -> { base = curCost[j - 1]; steps = curSteps[j - 1] }
                }
                curCost[j] = base + local
                curSteps[j] = steps + 1
            }
            // Swap rolling rows.
            val tCost = prevCost; prevCost = curCost; curCost = tCost
            val tSteps = prevSteps; prevSteps = curSteps; curSteps = tSteps
        }

        val totalSteps = prevSteps[m].coerceAtLeast(1)
        return prevCost[m] / totalSteps
    }

    /** Euclidean distance between two equal-length feature vectors. */
    private fun euclidean(x: FloatArray, y: FloatArray): Double {
        val len = minOf(x.size, y.size)
        var sum = 0.0
        for (k in 0 until len) {
            val d = (x[k] - y[k]).toDouble()
            sum += d * d
        }
        return sqrt(sum)
    }
}
