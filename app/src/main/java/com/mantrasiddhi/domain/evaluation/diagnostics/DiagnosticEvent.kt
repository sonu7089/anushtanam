/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.diagnostics

/**
 * A single on-device diagnostic record for a recitation the engine could not score
 * with full confidence (low STT confidence, or audio that was dropped/too noisy to
 * assess acoustically).
 *
 * These records exist to **grow the calibration corpus over time** (§5.4): they flag
 * exactly the real-world captures the evaluator struggled with, so they can later be
 * reviewed and labeled. They never leave the device.
 *
 * @property transcription The transcription that was evaluated.
 * @property overall Fused overall score in `0.0..1.0`.
 * @property correctness Correctness axis score.
 * @property completeness Completeness axis score.
 * @property pronunciation Pronunciation axis score.
 * @property lowConfidence Whether STT confidence was below the trust threshold.
 * @property audioCaptured Whether any learner audio was captured for this attempt.
 * @property acousticAssessed Whether pronunciation was grounded in the acoustic path.
 * @property snrDb Estimated capture SNR in dB (diagnostic), or `null` when no audio.
 * @property timestampMs Wall-clock time of the attempt, epoch millis.
 */
data class DiagnosticEvent(
    val transcription: String,
    val overall: Float,
    val correctness: Float,
    val completeness: Float,
    val pronunciation: Float,
    val lowConfidence: Boolean,
    val audioCaptured: Boolean,
    val acousticAssessed: Boolean,
    val snrDb: Float?,
    val timestampMs: Long
)
