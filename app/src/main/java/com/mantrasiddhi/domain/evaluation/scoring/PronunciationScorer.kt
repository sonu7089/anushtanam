/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.scoring

import com.mantrasiddhi.domain.evaluation.WordResult
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra

/**
 * Scores the **pronunciation** axis: the aggregate phonetic fidelity of the words
 * that were actually recited.
 *
 * Two deliberate choices:
 * - Only **aligned** words count (a skipped word is a completeness problem, not a
 *   pronunciation one), so this axis stays independent.
 * - Words are weighted by **importance** (syllable count) rather than a flat mean,
 *   so fumbling the four-syllable `prachodayat` matters more than the one-syllable
 *   `yo`. This replaces the old equal-weighting of all 13 words.
 */
object PronunciationScorer {

    private val syllableWeightByWord: Map<String, Int> =
        GayatriMantra.words.associate { it.transliteration to it.syllables.size }

    /**
     * Computes the importance-weighted pronunciation score in `0.0..1.0`.
     *
     * @param wordResults Per-reference-word results (missing words are ignored).
     * @return Weighted mean phonetic similarity of the aligned words, or `0.0`
     *   when nothing aligned.
     */
    fun score(wordResults: List<WordResult>): Float {
        val aligned = wordResults.filter { it.spokenWord != null }
        if (aligned.isEmpty()) return 0.0f
        var weightedSum = 0.0
        var totalWeight = 0.0
        for (result in aligned) {
            val weight = (syllableWeightByWord[result.referenceWord] ?: 1).toDouble()
            weightedSum += result.score * weight
            totalWeight += weight
        }
        if (totalWeight == 0.0) return 0.0f
        return (weightedSum / totalWeight).toFloat().coerceIn(0.0f, 1.0f)
    }
}
