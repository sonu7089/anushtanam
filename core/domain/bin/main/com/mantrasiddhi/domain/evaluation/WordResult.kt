/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Representation of the evaluation outcome for a single reference word.
 *
 * @property referenceWord The canonical reference word.
 * @property spokenWord The aligned spoken word, or `null` when the word was missing.
 * @property score The fused per-word pronunciation similarity in `0.0..1.0`.
 * @property feedback Categorical per-word feedback, or `null` when no issue.
 * @property acousticScore The acoustic (DTW-vs-benchmark) similarity for this word
 *   in `0.0..1.0`, or `null` when the word was not assessed from audio (text-only
 *   path, missing word, or audio gated out as noisy). Phase 3 signal.
 * @property pacingRatio Learner/benchmark duration ratio for this word (`1.0` is
 *   on-tempo, `>1.0` slower, `<1.0` faster), or `null` when not assessed.
 */
data class WordResult(
    val referenceWord: String,
    val spokenWord: String?,
    val score: Float,
    val feedback: WordFeedback?,
    val acousticScore: Float? = null,
    val pacingRatio: Float? = null
)
