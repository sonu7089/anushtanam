/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

/**
 * The acoustic pronunciation signal for a single reference word, derived from a
 * Dynamic-Time-Warping comparison of the learner's audio against the benchmark
 * recording segment for that word.
 *
 * @property wordIndex Zero-based reference word index this score belongs to.
 * @property score Acoustic pronunciation similarity to the benchmark in `0.0..1.0`,
 *   or `null` when the word could not be assessed acoustically (no audio captured
 *   for the segment, or the segment was gated out as too noisy).
 * @property pacingRatio Learner segment duration / benchmark segment duration. `1.0`
 *   is on-tempo; `> 1.0` is slower than the benchmark, `< 1.0` faster. `null` when
 *   not assessed.
 */
data class AcousticWordScore(
    val wordIndex: Int,
    val score: Float?,
    val pacingRatio: Float?
) {
    /** Whether a usable acoustic score is present for this word. */
    val isAssessed: Boolean get() = score != null

    /** Qualitative pacing read-out, useful for "too fast / too slow" feedback. */
    val pacing: Pacing
        get() = when {
            pacingRatio == null -> Pacing.UNKNOWN
            pacingRatio > SLOW_RATIO -> Pacing.TOO_SLOW
            pacingRatio < FAST_RATIO -> Pacing.TOO_FAST
            else -> Pacing.ON_TEMPO
        }

    /** Rhythm classification of a recited word relative to the benchmark. */
    enum class Pacing { TOO_FAST, ON_TEMPO, TOO_SLOW, UNKNOWN }

    private companion object {
        /** Above this learner/benchmark duration ratio the word is judged too slow. */
        const val SLOW_RATIO = 1.5f

        /** Below this learner/benchmark duration ratio the word is judged too fast. */
        const val FAST_RATIO = 0.66f
    }
}
