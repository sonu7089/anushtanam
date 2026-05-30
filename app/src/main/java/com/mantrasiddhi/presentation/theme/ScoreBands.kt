/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.theme

/**
 * Qualitative band a recitation/word score falls into, for presentation purposes.
 */
enum class ScoreLevel { STRONG, MODERATE, WEAK }

/**
 * Single presentation-facing band mapper for recitation scores.
 *
 * Replaces the `0.85f` literals that were previously duplicated across
 * `PracticeScreen`, `WordFeedbackGrid`, `FeedbackBottomSheet`, `WordMasteryGrid`
 * and `RecitationViewModel`, so the pass/strong cutoff is defined exactly once.
 */
object ScoreBands {

    /** Score at/above which a word/recitation is considered strong (mastered/correct). */
    const val STRONG_THRESHOLD = 0.85

    /** Default cutoff between moderate and weak (used by in-session feedback views). */
    const val MODERATE_THRESHOLD = 0.50

    /** Stricter moderate cutoff used by long-term mastery views. */
    const val MASTERY_MODERATE_THRESHOLD = 0.70

    /** Maps a 0..1 [score] to a [ScoreLevel] using [STRONG_THRESHOLD] and [moderateThreshold]. */
    fun levelFor(score: Double, moderateThreshold: Double = MODERATE_THRESHOLD): ScoreLevel = when {
        score >= STRONG_THRESHOLD -> ScoreLevel.STRONG
        score >= moderateThreshold -> ScoreLevel.MODERATE
        else -> ScoreLevel.WEAK
    }

    /** Float convenience overload. */
    fun levelFor(score: Float, moderateThreshold: Double = MODERATE_THRESHOLD): ScoreLevel =
        levelFor(score.toDouble(), moderateThreshold)
}
