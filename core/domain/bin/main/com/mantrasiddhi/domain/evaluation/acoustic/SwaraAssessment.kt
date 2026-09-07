/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

/**
 * The result of the optional **swara (pitch-accent) accuracy** layer (Phase 5.5).
 *
 * Correct Vedic Gayatri recitation carries pitch accents (udātta / anudātta /
 * svarita). None of the text or MFCC-timbre layers measure pitch; this layer compares
 * the learner's fundamental-frequency (f0) contour against the benchmark's, after
 * speaker-pitch normalisation, and reports a separate swara score plus a directional
 * coaching hint.
 *
 * This is **opt-in** ("Advanced / Vedic mode") and deliberately *separate* from the
 * three scoring axes — it never changes the normal correctness / completeness /
 * pronunciation scores or the pass verdict.
 *
 * @property wordScores Per-reference-word swara similarity in `0.0..1.0`, or `null`
 *   where pitch could not be tracked for that word. In reference word order.
 * @property overallScore Aggregate swara accuracy in `0.0..1.0` over assessed words.
 * @property hint A coaching hint about the overall pitch behaviour.
 * @property assessed Whether pitch could be assessed at all (false → feature off, no
 *   audio, or no voiced pitch found).
 */
data class SwaraAssessment(
    val wordScores: List<Float?>,
    val overallScore: Float,
    val hint: Hint,
    val assessed: Boolean
) {
    /** Coaching hint summarising the learner's pitch behaviour vs the benchmark. */
    enum class Hint {
        /** Pitch contour closely follows the benchmark. */
        GOOD,

        /** Learner is too monotone — little pitch movement where the benchmark moves. */
        FLAT,

        /** Pitch moves in the wrong direction (rising where it should fall, etc.). */
        WRONG_DIRECTION,

        /** Not enough signal to give a hint. */
        UNKNOWN
    }

    companion object {
        /** A "not assessed" swara result (feature off / no usable pitch). */
        fun notAssessed(): SwaraAssessment =
            SwaraAssessment(emptyList(), overallScore = 0f, hint = Hint.UNKNOWN, assessed = false)
    }
}
