/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.ProgressSummary
import kotlinx.coroutines.flow.Flow

/**
 * Cloud sync for the compact per-user progress summary (`progress/{uid}`), so the
 * Progress-tab headline stats and charts survive reinstall / device switch. Detailed
 * session history stays device-local (Room) by design.
 */
interface ProgressSyncRepository {

    /**
     * Live snapshots of the signed-in user's cloud summary. Emits `null` (and
     * completes) when signed out, and `null` while the doc does not exist yet.
     */
    fun summaryFlow(): Flow<ProgressSummary?>

    /**
     * Records one evaluated session ([overallScore] in `0..1`, [isCorrect] pass
     * verdict) into the cloud summary; seeds the doc from local history
     * ([localUserId]) on first use. Best-effort: never throws — a sync failure must
     * not affect the evaluation flow.
     */
    suspend fun recordSession(localUserId: Long, overallScore: Double, isCorrect: Boolean): Result<Unit>
}
