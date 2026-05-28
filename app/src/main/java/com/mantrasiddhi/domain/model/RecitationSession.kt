/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * Domain representation of a completed recitation session.
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
    val createdAt: Long = System.currentTimeMillis()
)
