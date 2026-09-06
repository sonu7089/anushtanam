/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import kotlin.math.exp

/**
 * Turns segmented learner audio into a per-word acoustic pronunciation score by
 * comparing each word against its benchmark segment with [Dtw].
 *
 * This is the signal the text engine could never produce: a word can be transcribed
 * "correctly" by the dictation engine yet be mispronounced, and only the acoustic
 * comparison against a known-good recitation reveals it (§3.3). It also derives a
 * per-word pacing ratio (learner vs benchmark duration) for rhythm feedback.
 *
 * Pure Kotlin: it consumes feature arrays supplied by the data layer and is fully
 * unit-testable with synthetic features.
 */
object AcousticPronunciationScorer {

    /**
     * Scores each reference word acoustically.
     *
     * @param learner The learner's full feature sequence.
     * @param segments Per-word inclusive frame ranges from [WordSegmenter] (one per
     *   reference word; `null` where the word could not be located).
     * @param benchmark Cached benchmark features per reference word (same order/size
     *   as [segments]).
     * @param distanceScale DTW-distance scaling: larger values are more forgiving.
     *   The score maps distance `d` to `exp(-d / distanceScale)`.
     * @param assessed Whether the capture passed the [AudioQualityGate]. When false,
     *   every word is reported unassessed so the engine falls back to text scoring.
     * @param snrDb Estimated SNR of the capture (carried through for diagnostics).
     * @return A populated [AcousticAssessment].
     */
    fun score(
        learner: AcousticFeatures,
        segments: List<IntRange?>,
        benchmark: List<AcousticFeatures>,
        distanceScale: Double,
        assessed: Boolean,
        snrDb: Float
    ): AcousticAssessment {
        val wordScores = benchmark.indices.map { i ->
            scoreWord(i, learner, segments.getOrNull(i), benchmark[i], distanceScale, assessed)
        }
        return AcousticAssessment(wordScores = wordScores, assessed = assessed, snrDb = snrDb)
    }

    private fun scoreWord(
        index: Int,
        learner: AcousticFeatures,
        range: IntRange?,
        benchmarkWord: AcousticFeatures,
        distanceScale: Double,
        assessed: Boolean
    ): AcousticWordScore {
        if (!assessed || range == null || benchmarkWord.isEmpty) {
            return AcousticWordScore(index, score = null, pacingRatio = null)
        }
        val learnerWord = learner.slice(range.first, range.last + 1)
        if (learnerWord.isEmpty) {
            return AcousticWordScore(index, score = null, pacingRatio = null)
        }
        val distance = Dtw.distance(learnerWord.frames, benchmarkWord.frames)
        val score = distanceToScore(distance, distanceScale)
        val pacing = pacingRatio(learnerWord.durationMs, benchmarkWord.durationMs)
        return AcousticWordScore(index, score = score, pacingRatio = pacing)
    }

    /** Maps a (non-negative) DTW distance to a `0.0..1.0` similarity. */
    private fun distanceToScore(distance: Double, scale: Double): Float {
        if (distance >= Double.MAX_VALUE / 4) return 0f
        val safeScale = if (scale <= 0.0) 1.0 else scale
        return exp(-distance / safeScale).toFloat().coerceIn(0f, 1f)
    }

    /** Learner/benchmark duration ratio, or `null` when the benchmark has no length. */
    private fun pacingRatio(learnerMs: Double, benchmarkMs: Double): Float? =
        if (benchmarkMs <= 0.0) null else (learnerMs / benchmarkMs).toFloat()
}
