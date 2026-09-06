/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

/**
 * Splits a learner's frame sequence into per-word frame ranges so each word can be
 * compared against its benchmark segment.
 *
 * The recogniser does not expose reliable per-word timestamps, so we combine two
 * cheap, robust signals:
 * 1. **Energy-based VAD** trims leading/trailing silence to find the voiced span.
 * 2. The voiced span is distributed across the reference words **in order**,
 *    proportional to each word's benchmark duration (a strong prior for how long
 *    each word should take), matching the word order from the Phase-2 text alignment.
 *
 * It is intentionally pure (operates on the energy envelope + benchmark durations)
 * so it is fully unit-testable without audio.
 */
object WordSegmenter {

    /**
     * Returns one frame range per reference word (in word order), or `null` for a
     * word when the audio is unusable.
     *
     * @param energies Per-frame short-time energy of the learner audio.
     * @param benchmarkWordDurationsMs Benchmark duration of each reference word, in
     *   ms and in word order — the proportional template for distribution.
     * @param energyThresholdRatio Fraction of the dynamic energy range above the
     *   noise floor that counts as "voiced" (`0.0..1.0`).
     * @param minVoicedFrames Minimum voiced frames required to attempt segmentation.
     * @return A list the same size as [benchmarkWordDurationsMs]; each entry is an
     *   inclusive `[startFrame, endFrame]` range, or `null` when nothing usable was
     *   captured.
     */
    fun segment(
        energies: FloatArray,
        benchmarkWordDurationsMs: List<Double>,
        energyThresholdRatio: Float,
        minVoicedFrames: Int
    ): List<IntRange?> {
        val wordCount = benchmarkWordDurationsMs.size
        if (wordCount == 0) return emptyList()
        val unassigned = List<IntRange?>(wordCount) { null }
        if (energies.isEmpty()) return unassigned

        val (voicedStart, voicedEnd) = voicedSpan(energies, energyThresholdRatio)
        val voicedFrames = voicedEnd - voicedStart + 1
        if (voicedStart < 0 || voicedFrames < minVoicedFrames) return unassigned

        return distribute(voicedStart, voicedEnd, benchmarkWordDurationsMs)
    }

    /** Finds the inclusive `[start, end]` frame indices of the voiced region. */
    private fun voicedSpan(energies: FloatArray, thresholdRatio: Float): Pair<Int, Int> {
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (e in energies) {
            if (e < min) min = e
            if (e > max) max = e
        }
        if (max <= min) return -1 to -1
        val threshold = min + thresholdRatio.coerceIn(0f, 1f) * (max - min)
        var start = -1
        var end = -1
        for (i in energies.indices) {
            if (energies[i] >= threshold) {
                if (start == -1) start = i
                end = i
            }
        }
        return start to end
    }

    /**
     * Distributes the inclusive voiced span across the words proportional to their
     * benchmark durations, preserving word order and full coverage.
     */
    private fun distribute(
        voicedStart: Int,
        voicedEnd: Int,
        durations: List<Double>
    ): List<IntRange?> {
        val totalDuration = durations.sum().takeIf { it > 0.0 } ?: return durations.map { null }
        val spanFrames = (voicedEnd - voicedStart + 1).toDouble()

        val result = ArrayList<IntRange?>(durations.size)
        var cumulative = 0.0
        for (i in durations.indices) {
            val startFrac = cumulative / totalDuration
            cumulative += durations[i]
            val endFrac = cumulative / totalDuration
            val s = voicedStart + (startFrac * spanFrames).toInt()
            // End is exclusive boundary minus one (inclusive); ensure at least one frame.
            val eExclusive = voicedStart + (endFrac * spanFrames).toInt()
            val e = (eExclusive - 1).coerceAtLeast(s).coerceAtMost(voicedEnd)
            result.add(s..e)
        }
        return result
    }
}
