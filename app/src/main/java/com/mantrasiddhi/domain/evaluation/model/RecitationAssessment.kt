/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.model

import com.mantrasiddhi.domain.evaluation.WordResult

/**
 * Rich, three-axis assessment of a recitation.
 *
 * Unlike the legacy single-scalar [com.mantrasiddhi.domain.evaluation.MantraEvaluation],
 * this model keeps the three learning goals separate so the learner can tell
 * *which* dimension needs work:
 *
 * - [correctness]: right words, right order, no swaps/extras.
 * - [completeness]: how much of the mantra (words & padas) was actually recited.
 * - [pronunciation]: phonetic fidelity (text-derived in Phase 1; acoustic later).
 *
 * [overall] is a documented fusion of the three axes. In Phase 1 it reproduces the
 * legacy mean-of-word-scores exactly (see `MantraEvaluator`) so no user-visible
 * behavior changes while the axes are introduced.
 *
 * @property correctness Sequence/order correctness axis.
 * @property completeness Coverage axis.
 * @property pronunciation Phonetic-fidelity axis.
 * @property overall Fused headline score.
 * @property wordResults Per-reference-word breakdown (always one entry per reference word).
 * @property padaResults Per-line breakdown.
 * @property extraWords Spoken words that did not align to any reference word.
 * @property isPass Whether [overall] meets the configured pass threshold (sensitivity).
 * @property recitationTimeMs Duration of the recitation in milliseconds.
 */
data class RecitationAssessment(
    val correctness: AxisScore,
    val completeness: AxisScore,
    val pronunciation: AxisScore,
    val overall: AxisScore,
    val wordResults: List<WordResult>,
    val padaResults: List<PadaResult>,
    val extraWords: List<String>,
    val isPass: Boolean,
    val recitationTimeMs: Long
) {
    /** Reference words that were not recited at all. */
    val missingWords: List<String>
        get() = wordResults.filter { it.spokenWord == null }.map { it.referenceWord }
}
