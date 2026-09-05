/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import com.mantrasiddhi.domain.evaluation.EvaluationConfig

/**
 * Domain port for the optional swara/pitch-accuracy layer (Phase 5.5).
 *
 * Like [AcousticAnalyzer] the implementation lives in the data layer (it needs f0
 * extraction and the benchmark audio) but the contract is owned by the domain so the
 * evaluation use case stays Android-free and the analyzer can be faked in tests.
 *
 * Implementations must **degrade gracefully**: any failure or absence of usable pitch
 * returns [SwaraAssessment.notAssessed] rather than throwing.
 */
interface SwaraAnalyzer {

    /**
     * Extracts and compares the learner's pitch contour against the benchmark.
     *
     * @param audio The learner's captured recitation.
     * @param config Tunable thresholds (swara distance scale, min voiced frames, …).
     * @return The swara assessment; never throws.
     */
    suspend fun analyze(
        audio: RecordedAudio,
        config: EvaluationConfig = EvaluationConfig.DEFAULT
    ): SwaraAssessment
}
