/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.scoring

import com.mantrasiddhi.domain.evaluation.EvaluationConfig
import com.mantrasiddhi.domain.evaluation.WordResult
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAssessment
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra

/**
 * Scores the **pronunciation** axis: the aggregate phonetic fidelity of the words
 * that were actually recited.
 *
 * Three deliberate choices:
 * - Only **aligned** words count (a skipped word is a completeness problem, not a
 *   pronunciation one), so this axis stays independent.
 * - Words are weighted by **importance** (syllable count) rather than a flat mean,
 *   so fumbling the four-syllable `prachodayat` matters more than the one-syllable
 *   `yo`. This replaces the old equal-weighting of all 13 words.
 * - Phase 3 **fuses** the per-word acoustic (DTW-vs-benchmark) signal with the
 *   text-phonetic estimate. Acoustic is the primary pronunciation signal because it
 *   reflects how the word actually *sounded*; the text-phonetic score is the
 *   fallback when audio is missing/low-quality or a word could not be located
 *   acoustically — giving graceful degradation to the Phase-2 behaviour.
 */
object PronunciationScorer {

    private val syllableWeightByWord: Map<String, Int> =
        GayatriMantra.words.associate { it.transliteration to it.syllables.size }

    private val indexByWord: Map<String, Int> =
        GayatriMantra.words.associate { it.transliteration to it.index }

    /**
     * Computes the importance-weighted, text-only pronunciation score in `0.0..1.0`.
     *
     * Kept for the graceful-degradation path (no audio) and for backwards
     * compatibility with the Phase-2 behaviour.
     *
     * @param wordResults Per-reference-word results (missing words are ignored).
     * @return Weighted mean phonetic similarity of the aligned words, or `0.0`
     *   when nothing aligned.
     */
    fun score(wordResults: List<WordResult>): Float =
        score(wordResults, acoustic = null, config = EvaluationConfig.DEFAULT)

    /**
     * Computes the importance-weighted pronunciation score, fusing the acoustic
     * signal per word when [acoustic] is present and assessed.
     *
     * For each aligned word the effective per-word score is:
     * - `acousticWeight * acoustic + (1 - acousticWeight) * text` when an acoustic
     *   score exists for that word, otherwise just `text` (graceful fallback).
     *
     * @param wordResults Per-reference-word results (missing words are ignored).
     * @param acoustic The acoustic assessment, or `null` to use the text path.
     * @param config Supplies [EvaluationConfig.acousticPronunciationWeight].
     * @return Weighted mean fused per-word similarity, or `0.0` when nothing aligned.
     */
    fun score(
        wordResults: List<WordResult>,
        acoustic: AcousticAssessment?,
        config: EvaluationConfig
    ): Float {
        val aligned = wordResults.filter { it.spokenWord != null }
        if (aligned.isEmpty()) return 0.0f
        val useAcoustic = acoustic != null && acoustic.assessed
        var weightedSum = 0.0
        var totalWeight = 0.0
        for (result in aligned) {
            val weight = (syllableWeightByWord[result.referenceWord] ?: 1).toDouble()
            val effective = effectiveScore(result, acoustic, useAcoustic, config)
            weightedSum += effective * weight
            totalWeight += weight
        }
        if (totalWeight == 0.0) return 0.0f
        return (weightedSum / totalWeight).toFloat().coerceIn(0.0f, 1.0f)
    }

    /** Fuses the text and (optional) acoustic per-word scores into one value. */
    private fun effectiveScore(
        result: WordResult,
        acoustic: AcousticAssessment?,
        useAcoustic: Boolean,
        config: EvaluationConfig
    ): Float {
        if (!useAcoustic) return result.score
        val index = indexByWord[result.referenceWord] ?: return result.score
        val acousticScore = acoustic?.scoreFor(index) ?: return result.score
        val w = config.acousticPronunciationWeight.coerceIn(0f, 1f)
        return (w * acousticScore + (1f - w) * result.score).coerceIn(0f, 1f)
    }
}
