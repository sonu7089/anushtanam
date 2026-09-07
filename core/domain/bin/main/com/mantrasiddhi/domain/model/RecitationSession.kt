/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * Domain representation of a completed recitation session.
 *
 * @property mantraId Id of the mantra that was recited. Per-word analytics (mastery, weak
 *   words, the practice plan) are only comparable within one mantra, so they filter on
 *   this; sessions recorded before the app carried more than one mantra carry the id of
 *   the mantra it shipped with.
 * @property wordScores Per-word score keyed by the reference word's transliteration, which
 *   is unique within a mantra but not across the catalog — hence [mantraId].
 * @property overallScore The fused headline score in `0.0..1.0`.
 * @property correctness Sequence/order correctness axis (`0.0..1.0`). Phase 4 adds
 *   per-axis persistence so progress can show three independent trend lines.
 * @property completeness Coverage axis (`0.0..1.0`).
 * @property pronunciation Phonetic-fidelity axis (`0.0..1.0`).
 */
data class RecitationSession(
    val id: Long = 0,
    val userId: Long,
    val mantraId: String,
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
