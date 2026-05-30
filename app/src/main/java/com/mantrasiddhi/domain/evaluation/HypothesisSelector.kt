/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Selects the best transcription among the n-best hypotheses returned by speech
 * recognition.
 *
 * The 1-best string from a dictation engine is not always the most faithful to the
 * reference mantra — the correct word frequently sits in hypothesis #2. Rather than
 * scoring only the top guess, we score every hypothesis and keep the one that aligns
 * best to the mantra, breaking ties by the recognizer's own confidence.
 */
object HypothesisSelector {

    /**
     * Picks the best-aligning hypothesis.
     *
     * @param hypotheses Candidate transcriptions paired with their STT confidence,
     *   ordered best-first as the recognizer ranked them.
     * @param scorer Returns an alignment score in `0.0..1.0` for a transcription
     *   (typically the engine's overall assessment score).
     * @return The chosen transcription with its confidence, or `null` when there are
     *   no usable hypotheses.
     */
    fun select(
        hypotheses: List<Pair<String, Float>>,
        scorer: (String) -> Float
    ): Pair<String, Float>? {
        val usable = hypotheses.filter { it.first.isNotBlank() }
        if (usable.isEmpty()) return null
        return usable.maxWithOrNull(
            compareBy<Pair<String, Float>> { scorer(it.first) }.thenBy { it.second }
        )
    }
}
