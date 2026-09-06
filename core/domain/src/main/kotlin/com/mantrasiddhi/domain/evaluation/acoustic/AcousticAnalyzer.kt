/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import com.mantrasiddhi.domain.evaluation.EvaluationConfig
import com.mantrasiddhi.domain.mantra.Mantra

/**
 * Domain port for the acoustic pronunciation layer.
 *
 * The implementation lives in the data layer (it needs DSP + the bundled benchmark
 * audio), but the contract is owned by the domain so [com.mantrasiddhi.domain
 * .usecase.EvaluateRecitationUseCase] can stay Android-free and the analyzer can be
 * faked in tests.
 *
 * Implementations must **degrade gracefully**: any failure (no audio, extraction
 * error, device without a usable mic) returns [AcousticAssessment.notAssessed]
 * rather than throwing, so the text-only path always remains available.
 */
interface AcousticAnalyzer {

    /**
     * Analyses captured [audio] against the benchmark recitation and returns a
     * per-word acoustic pronunciation assessment.
     *
     * @param mantra The mantra recited; supplies the benchmark recording and the per-word
     *   windows the learner's audio is segmented against. A mantra with no benchmark
     *   yields [AcousticAssessment.notAssessed].
     * @param audio The learner's captured recitation. An empty recording yields
     *   [AcousticAssessment.notAssessed].
     * @param sttConfidence STT confidence of the chosen transcription in `0.0..1.0`,
     *   used by the quality gate to avoid scoring unclear audio.
     * @param config Tunable thresholds (SNR floor, distance scaling, etc.).
     * @return The acoustic assessment; never throws.
     */
    suspend fun analyze(
        mantra: Mantra,
        audio: RecordedAudio,
        sttConfidence: Float,
        config: EvaluationConfig = EvaluationConfig.DEFAULT
    ): AcousticAssessment
}
