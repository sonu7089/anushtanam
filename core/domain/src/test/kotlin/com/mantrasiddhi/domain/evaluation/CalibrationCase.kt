/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * A single human-labeled recitation case used to calibrate and validate the
 * evaluation engine against human judgment.
 *
 * The text path ([transcription]) drives correctness, completeness and the
 * text-phonetic pronunciation estimate. [audioAsset] names the recorded sample that
 * an acoustic run would consume (Phase 3+); in the on-device-free CI harness the
 * acoustic layer is not exercised, so cases whose pronunciation degradation is
 * *only* audible (correct spelling, wrong sound — e.g. dropped aspiration that the
 * dictation engine auto-corrects) are flagged with [pronunciationNeedsAudio] and
 * excluded from the **text** pronunciation-agreement metric. This keeps the gate
 * honest: it never asks the text path to detect something only audio can.
 *
 * @property id Stable identifier for the case.
 * @property transcription Raw STT-style text to feed the engine.
 * @property errorClass The error class this case exercises (for the coverage report),
 *   e.g. `"clean"`, `"missing-word"`, `"missing-pada"`, `"swap"`, `"substitution"`,
 *   `"extra"`, `"phonetic"`, `"de-aspiration"`, `"visarga"`, `"gibberish"`.
 * @property audioAsset Optional recorded sample for acoustic scoring (Phase 3+).
 * @property humanCorrectness Human-assigned correctness label, 0.0..1.0 — judges
 *   only the words/order that were spoken (missing words are a completeness matter).
 * @property humanCompleteness Human-assigned completeness label, 0.0..1.0.
 * @property humanPronunciation Human-assigned pronunciation label, 0.0..1.0.
 * @property expectedPass Human verdict on whether this recitation should pass.
 * @property pronunciationNeedsAudio True when the pronunciation label can only be
 *   reproduced from audio (the text is spelled correctly); such cases are excluded
 *   from the text-path pronunciation-agreement metric.
 */
data class CalibrationCase(
    val id: String,
    val transcription: String,
    val errorClass: String,
    val audioAsset: String?,
    val humanCorrectness: Float,
    val humanCompleteness: Float,
    val humanPronunciation: Float,
    val expectedPass: Boolean,
    val pronunciationNeedsAudio: Boolean = false
)
