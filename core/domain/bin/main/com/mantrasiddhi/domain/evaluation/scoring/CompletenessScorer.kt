/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.scoring

import com.mantrasiddhi.domain.evaluation.WordResult
import com.mantrasiddhi.domain.evaluation.model.PadaResult

/**
 * Scores the **completeness** axis: how much of the mantra (words and padas) was
 * actually recited, independent of how well it was pronounced.
 *
 * Missing a whole pada is penalised more heavily than dropping the same number of
 * scattered words, because losing an entire line is a more serious structural gap
 * than fumbling individual words.
 */
object CompletenessScorer {

    /**
     * Computes the completeness score in `0.0..1.0`.
     *
     * @param wordResults Per-reference-word results (one entry per reference word).
     * @param padaResults Per-pada breakdown, used to detect fully-missing lines.
     * @param wholePadaPenalty Extra penalty subtracted for each fully-missing pada.
     */
    fun score(
        wordResults: List<WordResult>,
        padaResults: List<PadaResult>,
        wholePadaPenalty: Float
    ): Float {
        if (wordResults.isEmpty()) return 0.0f
        val recited = wordResults.count { it.spokenWord != null }
        val coverage = recited.toFloat() / wordResults.size
        val missingPadas = padaResults.count { it.wordResults.isNotEmpty() && it.coverage == 0.0f }
        val penalty = missingPadas * wholePadaPenalty
        return (coverage - penalty).coerceIn(0.0f, 1.0f)
    }
}
