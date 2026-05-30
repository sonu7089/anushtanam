/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.diagnostics

/**
 * Domain port for **on-device, no-network** evaluation telemetry (§5.4).
 *
 * The evaluation use case reports low-confidence / not-assessed recitations through
 * this port so they can be reviewed later and used to grow the calibration corpus.
 * Implementations must be best-effort and **never throw** — diagnostics must never
 * disrupt a recitation — and must keep all data on the device (no network).
 */
interface EvaluationDiagnostics {

    /**
     * Records a single [event]. Best-effort: implementations swallow their own
     * failures and run off the main thread.
     */
    suspend fun record(event: DiagnosticEvent)
}
