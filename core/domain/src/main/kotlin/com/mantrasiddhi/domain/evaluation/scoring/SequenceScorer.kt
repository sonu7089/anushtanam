/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.scoring

import com.mantrasiddhi.domain.evaluation.AlignmentStep
import com.mantrasiddhi.domain.evaluation.EvaluationConfig
import com.mantrasiddhi.domain.evaluation.WordFeedback
import com.mantrasiddhi.domain.evaluation.WordResult

/**
 * Scores the **correctness** (order/sequence) axis: right words, right order, no
 * swaps, substitutions or extra insertions.
 *
 * This replaces the old "matches any reference slot ≥ 0.85" swap hack. Out-of-order
 * recitation is detected as *crossings* (inversions) in the word alignment, so two
 * transposed words are penalised as a sequence error even though every word is
 * present. Missing words are deliberately **not** penalised here — that is the
 * completeness axis's job — keeping the two axes independent.
 */
object SequenceScorer {

    /**
     * Computes the correctness score in `0.0..1.0`.
     *
     * @param wordResults Per-reference-word results.
     * @param alignment The word-level alignment (used to detect order inversions).
     * @param extraWordCount Number of spoken words that did not align to any reference word.
     * @param config Penalty weights and the match threshold.
     */
    fun score(
        wordResults: List<WordResult>,
        alignment: List<AlignmentStep>,
        extraWordCount: Int,
        config: EvaluationConfig
    ): Float {
        if (wordResults.isEmpty()) return 0.0f
        val swapped = wordResults.count { it.feedback == WordFeedback.SWAPPED }
        val wrong = wordResults.count {
            it.spokenWord != null && it.feedback != WordFeedback.SWAPPED && it.score < config.matchThreshold
        }
        val inversions = countInversions(alignment)
        val penalty = wrong * config.wrongWordPenalty +
            swapped * config.swappedWordPenalty +
            extraWordCount * config.extraWordPenalty +
            inversions * config.inversionPenalty
        return (1.0f - penalty / wordResults.size).coerceIn(0.0f, 1.0f)
    }

    /**
     * Counts order inversions among aligned pairs: pairs of reference words whose
     * spoken positions appear in the wrong relative order (a true sequence swap).
     */
    private fun countInversions(alignment: List<AlignmentStep>): Int {
        val spokenOrder = alignment
            .filter { it.refIndex != null && it.spokenIndex != null }
            .sortedBy { it.refIndex }
            .map { it.spokenIndex!! }
        var inversions = 0
        for (i in spokenOrder.indices) {
            for (j in i + 1 until spokenOrder.size) {
                if (spokenOrder[i] > spokenOrder[j]) inversions++
            }
        }
        return inversions
    }
}
