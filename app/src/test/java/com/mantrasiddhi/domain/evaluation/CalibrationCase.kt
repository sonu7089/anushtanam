/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * A single human-labeled recitation case used to calibrate and validate the
 * evaluation engine against human judgment.
 *
 * In Phases 1–2 only [transcription] is exercised (text path). [audioAsset] is
 * reserved for the acoustic pronunciation work in Phase 3+.
 *
 * @property id Stable identifier for the case.
 * @property transcription Raw STT-style text to feed the engine.
 * @property audioAsset Optional recorded sample for acoustic scoring (Phase 3+).
 * @property humanCorrectness Human-assigned correctness label, 0.0..1.0.
 * @property humanCompleteness Human-assigned completeness label, 0.0..1.0.
 * @property humanPronunciation Human-assigned pronunciation label, 0.0..1.0.
 * @property expectedPass Human verdict on whether this recitation should pass.
 */
data class CalibrationCase(
    val id: String,
    val transcription: String,
    val audioAsset: String?,
    val humanCorrectness: Float,
    val humanCompleteness: Float,
    val humanPronunciation: Float,
    val expectedPass: Boolean
)
