/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.acoustic.SwaraAssessment

/**
 * Result of evaluating a recitation session against the reference mantra.
 *
 * @property lowConfidence True when the STT confidence was too low to trust the
 *   transcription; callers should surface an "unclear audio, try again" message
 *   rather than a low pronunciation score.
 * @property acousticAssessed True when the pronunciation score was grounded in the
 *   acoustic (audio-vs-benchmark) signal. `false` means it is the text-only estimate
 *   (no audio captured, or audio gated out as too noisy) — the UI may note that
 *   pronunciation was not assessed from audio rather than implying a confident score.
 * @property swara Optional Vedic swara/pitch assessment (§5.5), present only when the
 *   user has enabled Advanced/Vedic mode and pitch could be tracked. It is a separate,
 *   opt-in signal and never affects [overallScore] or [isCorrect].
 */
data class MantraEvaluation(
    val overallScore: Float,
    val wordResults: List<WordResult>,
    val isCorrect: Boolean,
    val missingWords: List<String>,
    val extraWords: List<String>,
    val recitationTimeMs: Long,
    val lowConfidence: Boolean = false,
    val acousticAssessed: Boolean = false,
    val swara: SwaraAssessment? = null
)
