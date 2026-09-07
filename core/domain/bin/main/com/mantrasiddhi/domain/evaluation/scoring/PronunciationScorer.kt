/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.scoring

import com.mantrasiddhi.domain.evaluation.EvaluationConfig
import com.mantrasiddhi.domain.evaluation.WordResult
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAssessment
import com.mantrasiddhi.domain.mantra.Mantra

/**
 * Scores the **pronunciation** axis: the aggregate phonetic fidelity of the words that
 * were actually recited.
 *
 * Three deliberate choices:
 * - Only **aligned** words count (a skipped word is a completeness problem, not a
 *   pronunciation one), so this axis stays independent.
 * - Words are weighted by **importance** (syllable count) rather than a flat mean, so
 *   fumbling the four-syllable `prachodayat` matters more than the one-syllable `yo`.
 * - The per-word acoustic (DTW-vs-benchmark) signal is **fused** with the text-phonetic
 *   estimate. Acoustic is the primary pronunciation signal because it reflects how the
 *   word actually *sounded*; the text-phonetic score is the fallback when audio is
 *   missing or low-quality, or a word could not be located acoustically.
 */
object PronunciationScorer {

    /**
     * Computes the importance-weighted pronunciation score in `0.0..1.0`, fusing the
     * acoustic signal per word when [acoustic] is present and assessed.
     *
     * For each aligned word the effective score is
     * `acousticWeight * acoustic + (1 - acousticWeight) * text` when an acoustic score
     * exists for that word, and just `text` otherwise.
     *
     * @param mantra The mantra recited; supplies each word's syllable weight and position.
     * @param wordResults Per-reference-word results (missing words are ignored).
     * @param acoustic The acoustic assessment, or `null` to use the text-only path.
     * @param config Supplies [EvaluationConfig.acousticPronunciationWeight].
     * @return Weighted mean per-word similarity, or `0.0` when nothing aligned.
     */
    fun score(
        mantra: Mantra,
        wordResults: List<WordResult>,
        acoustic: AcousticAssessment? = null,
        config: EvaluationConfig = EvaluationConfig.DEFAULT
    ): Float {
        val aligned = wordResults.filter { it.spokenWord != null }
        if (aligned.isEmpty()) return 0.0f

        val useAcoustic = acoustic != null && acoustic.assessed
        var weightedSum = 0.0
        var totalWeight = 0.0
        for (result in aligned) {
            val weight = mantra.syllableCountFor(result.referenceWord).toDouble()
            weightedSum += effectiveScore(mantra, result, acoustic, useAcoustic, config) * weight
            totalWeight += weight
        }
        if (totalWeight == 0.0) return 0.0f
        return (weightedSum / totalWeight).toFloat().coerceIn(0.0f, 1.0f)
    }

    /** Fuses the text and (optional) acoustic per-word scores into one value. */
    private fun effectiveScore(
        mantra: Mantra,
        result: WordResult,
        acoustic: AcousticAssessment?,
        useAcoustic: Boolean,
        config: EvaluationConfig
    ): Float {
        if (!useAcoustic) return result.score
        val index = mantra.indexOf(result.referenceWord) ?: return result.score
        val acousticScore = acoustic?.scoreFor(index) ?: return result.score
        val weight = config.acousticPronunciationWeight.coerceIn(0f, 1f)
        return (weight * acousticScore + (1f - weight) * result.score).coerceIn(0f, 1f)
    }
}
