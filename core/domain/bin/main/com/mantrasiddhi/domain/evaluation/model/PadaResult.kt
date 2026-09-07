/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.model

import com.mantrasiddhi.domain.evaluation.WordResult

/**
 * Per-pada (per-line) breakdown of a recitation.
 *
 * The Gayatri Mantra is taught as four padas; surfacing completeness and quality
 * at the pada level lets feedback say "you dropped the third line" rather than
 * only flagging individual words.
 *
 * @property pada The 1-based pada (line) number, 1..4.
 * @property coverage Fraction of this pada's words that were actually recited, 0.0..1.0.
 * @property averageScore Mean per-word similarity for the words of this pada, 0.0..1.0.
 * @property wordResults The individual word results that make up this pada.
 */
data class PadaResult(
    val pada: Int,
    val coverage: Float,
    val averageScore: Float,
    val wordResults: List<WordResult>
)
