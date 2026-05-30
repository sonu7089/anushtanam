/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.model.AxisScore
import com.mantrasiddhi.domain.evaluation.model.ScoreBand

/**
 * All tunable thresholds and axis weights for the evaluation engine in one place.
 *
 * Previously these were scattered as hardcoded `private const` values inside
 * [MantraEvaluator] and duplicated literals across the UI. Centralizing them here
 * makes the engine configurable (notably wiring the user's
 * `recitationSensitivity` preference into [passThreshold]) and removes magic
 * numbers.
 *
 * @property passThreshold Overall score at/above which a recitation is a "pass".
 *   Wired from the user's recitation-sensitivity preference. Also used as the
 *   top ([ScoreBand.EXCELLENT]) band cutoff so the slider visibly shifts the band.
 * @property goodBand Lower cutoff for the [ScoreBand.GOOD] band.
 * @property needsWorkBand Lower cutoff for the [ScoreBand.NEEDS_WORK] band (below = [ScoreBand.POOR]).
 * @property feedbackThreshold Per-word score below which feedback is generated.
 * @property swapThreshold Similarity at which a word is judged to match a *different*
 *   reference slot (i.e. an out-of-order/swapped word).
 * @property partialThreshold Per-word score at/above which a mismatch is "partial"
 *   rather than a full pronunciation miss.
 * @property matchThreshold Minimum per-word similarity for a word to count toward
 *   correctness (not just having been aligned).
 * @property correctnessWeight Weight of the correctness axis in [fuseOverall].
 * @property completenessWeight Weight of the completeness axis in [fuseOverall].
 * @property pronunciationWeight Weight of the pronunciation axis in [fuseOverall].
 */
data class EvaluationConfig(
    val passThreshold: Float = 0.85f,
    val goodBand: Float = 0.70f,
    val needsWorkBand: Float = 0.40f,
    val feedbackThreshold: Float = 0.70f,
    val swapThreshold: Float = 0.85f,
    val partialThreshold: Float = 0.40f,
    val matchThreshold: Float = 0.50f,
    val correctnessWeight: Float = 0.34f,
    val completenessWeight: Float = 0.33f,
    val pronunciationWeight: Float = 0.33f
) {

    /** Maps a 0..1 [score] to a qualitative [ScoreBand] using the configured cutoffs. */
    fun bandFor(score: Float): ScoreBand = when {
        score >= passThreshold -> ScoreBand.EXCELLENT
        score >= goodBand -> ScoreBand.GOOD
        score >= needsWorkBand -> ScoreBand.NEEDS_WORK
        else -> ScoreBand.POOR
    }

    /** Convenience: wraps a [score] together with its [ScoreBand]. */
    fun axis(score: Float): AxisScore = AxisScore(score, bandFor(score))

    /**
     * Weighted fusion of the three axes into a single 0..1 score.
     *
     * Reserved for the Phase 2 scoring redesign. Phase 1 deliberately keeps the
     * headline `overall` score equal to the legacy mean-of-word-scores to
     * guarantee no behavioral regression, so this helper is provided for callers
     * that explicitly want a three-axis blend.
     */
    fun fuseOverall(correctness: Float, completeness: Float, pronunciation: Float): Float {
        val totalWeight = correctnessWeight + completenessWeight + pronunciationWeight
        if (totalWeight <= 0f) return 0f
        val weighted = correctness * correctnessWeight +
            completeness * completenessWeight +
            pronunciation * pronunciationWeight
        return (weighted / totalWeight).coerceIn(0f, 1f)
    }

    companion object {
        /** Default configuration; reproduces the historical 0.85-gate behavior. */
        val DEFAULT = EvaluationConfig()
    }
}
