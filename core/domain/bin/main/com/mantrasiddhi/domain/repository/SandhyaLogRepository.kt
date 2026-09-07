/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.SandhyaLog
import com.mantrasiddhi.domain.model.SandhyaPeriod
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for Sandhya Vandan occurrence logs (one row per scheduled juncture).
 */
interface SandhyaLogRepository {

    /** Streams all logs for [userId], newest first; drives the metrics dashboard. */
    fun observeLogs(userId: Long): Flow<List<SandhyaLog>>

    /** All logs for [userId] as a one-shot read. */
    suspend fun getLogs(userId: Long): List<SandhyaLog>

    /**
     * Records that a reminder fired for [period] on [dateKey]. No-ops if a row for
     * that day+period already exists, so it can never clobber an existing OBSERVED
     * outcome when a reminder re-fires.
     */
    suspend fun logNotified(userId: Long, period: SandhyaPeriod, scheduledEpochMillis: Long, dateKey: String)

    /** Marks the [period] occurrence on [dateKey] as performed by the user. */
    suspend fun markObserved(userId: Long, period: SandhyaPeriod, dateKey: String, observedEpochMillis: Long)

    /** Flips still-NOTIFIED rows from before [todayKey] to MISSED. */
    suspend fun sweepMissed(userId: Long, todayKey: String)
}
