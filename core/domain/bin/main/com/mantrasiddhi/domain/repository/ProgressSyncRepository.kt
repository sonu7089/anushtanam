/*
 * Anushtanam - Vedic mantra recitation app
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
 * session history is backed up as immutable canonical records.
 */
interface ProgressSyncRepository {

    /**
     * Live snapshots of the signed-in user's cloud summary. Emits `null` (and
     * completes) when signed out, and `null` while the doc does not exist yet.
     */
    fun summaryFlow(): Flow<ProgressSummary?>

    /** Retired anonymous API. Counts must commit with a stable saved session. */
    @Deprecated("Use RecitationRepository.saveSession; it commits every counting event")
    suspend fun recordSession(localUserId: Long, overallScore: Double, isCorrect: Boolean): Result<Unit>
}
