/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Representation of the evaluation outcome for a single reference word.
 */
data class WordResult(
    val referenceWord: String,
    val spokenWord: String?,
    val score: Float,
    val feedback: WordFeedback?
)
