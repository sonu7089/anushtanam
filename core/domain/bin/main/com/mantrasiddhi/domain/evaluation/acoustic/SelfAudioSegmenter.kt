/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

/**
 * Maps a reference word onto the **sample range** of the learner's own recording, so
 * the UI can play back just that word for A/B comparison against the benchmark
 * (Phase 4.2).
 *
 * It reuses the same robust approach as [WordSegmenter] — trim silence with an
 * energy-based VAD, then distribute the voiced span across the words proportional to
 * each word's benchmark duration — but works in *sample* space (windowed energy) so
 * its output can drive raw PCM playback directly. Pure and unit-testable.
 */
object SelfAudioSegmenter {

    /** Energy window length in samples (~20 ms at 16 kHz). */
    private const val WINDOW_SAMPLES = 320

    /** Minimum voiced windows required before a segmentation is attempted. */
    private const val MIN_VOICED_WINDOWS = 3

    /** Default fraction of the dynamic energy range that counts as voiced. */
    private const val DEFAULT_THRESHOLD_RATIO = 0.15f

    /**
     * Returns the inclusive sample range `[start, end]` of [wordIndex] inside [audio],
     * or `null` when the recording is empty/too short to segment.
     *
     * @param audio The learner's captured recitation.
     * @param benchmarkWordDurationsMs Benchmark duration of each reference word (ms),
     *   in word order — the proportional template for distribution.
     * @param wordIndex The reference word to locate.
     * @param thresholdRatio VAD threshold as a fraction of the dynamic energy range.
     */
    fun sampleRangeFor(
        audio: RecordedAudio,
        benchmarkWordDurationsMs: List<Double>,
        wordIndex: Int,
        thresholdRatio: Float = DEFAULT_THRESHOLD_RATIO
    ): IntRange? {
        if (wordIndex !in benchmarkWordDurationsMs.indices) return null
        val frameRanges = segmentFrames(audio, benchmarkWordDurationsMs, thresholdRatio)
        val frameRange = frameRanges.getOrNull(wordIndex) ?: return null
        val start = frameRange.first * WINDOW_SAMPLES
        val end = ((frameRange.last + 1) * WINDOW_SAMPLES - 1).coerceAtMost(audio.samples.size - 1)
        if (start > end) return null
        return start..end
    }

    private fun segmentFrames(
        audio: RecordedAudio,
        benchmarkWordDurationsMs: List<Double>,
        thresholdRatio: Float
    ): List<IntRange?> {
        val energies = windowEnergies(audio.samples)
        return WordSegmenter.segment(
            energies = energies,
            benchmarkWordDurationsMs = benchmarkWordDurationsMs,
            energyThresholdRatio = thresholdRatio,
            minVoicedFrames = MIN_VOICED_WINDOWS
        )
    }

    /** Computes mean-square energy per fixed window over [samples]. */
    private fun windowEnergies(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        val windowCount = (samples.size + WINDOW_SAMPLES - 1) / WINDOW_SAMPLES
        val energies = FloatArray(windowCount)
        for (w in 0 until windowCount) {
            val start = w * WINDOW_SAMPLES
            val end = minOf(start + WINDOW_SAMPLES, samples.size)
            var sum = 0.0
            for (i in start until end) sum += samples[i] * samples[i]
            energies[w] = (sum / (end - start)).toFloat()
        }
        return energies
    }
}
