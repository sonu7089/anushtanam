/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Result of evaluating a recitation session against the reference mantra.
 *
 * @property lowConfidence True when the STT confidence was too low to trust the
 *   transcription; callers should surface an "unclear audio, try again" message
 *   rather than a low pronunciation score.
 */
data class MantraEvaluation(
    val overallScore: Float,
    val wordResults: List<WordResult>,
    val isCorrect: Boolean,
    val missingWords: List<String>,
    val extraWords: List<String>,
    val recitationTimeMs: Long,
    val lowConfidence: Boolean = false
)
