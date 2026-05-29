/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Result of evaluating a recitation session against the reference mantra.
 */
data class MantraEvaluation(
    val overallScore: Float,
    val wordResults: List<WordResult>,
    val isCorrect: Boolean,
    val missingWords: List<String>,
    val extraWords: List<String>,
    val recitationTimeMs: Long
)
