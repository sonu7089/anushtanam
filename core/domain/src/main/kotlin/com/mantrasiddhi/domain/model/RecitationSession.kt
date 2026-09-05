/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * Domain representation of a completed recitation session.
 *
 * @property overallScore The fused headline score in `0.0..1.0`.
 * @property correctness Sequence/order correctness axis (`0.0..1.0`). Phase 4 adds
 *   per-axis persistence so progress can show three independent trend lines.
 * @property completeness Coverage axis (`0.0..1.0`).
 * @property pronunciation Phonetic-fidelity axis (`0.0..1.0`).
 */
data class RecitationSession(
    val id: Long = 0,
    val userId: Long,
    val mode: RecitationMode,
    val overallScore: Double,
    val wordScores: Map<String, Double>,
    val transcription: String,
    val isCorrect: Boolean,
    val durationMs: Long,
    val correctness: Double = overallScore,
    val completeness: Double = overallScore,
    val pronunciation: Double = overallScore,
    val createdAt: Long = System.currentTimeMillis()
)
