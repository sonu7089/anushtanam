/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Threshold tuning & agreement reporting (Phase 5.2).
 *
 * Sweeps the tunable [EvaluationConfig] parameters against the golden corpus and
 * reports the agreement landscape (per-axis rank correlation + pass/fail agreement)
 * so the chosen defaults are *justified by data*, not guessed. Run it manually to
 * re-tune; it also asserts that the shipped [EvaluationConfig.DEFAULT] is at least as
 * good as every neighbouring point swept here, i.e. that the defaults sit at the
 * agreement optimum we claim.
 *
 * The values selected by this sweep are documented inline in [EvaluationConfig].
 */
class ThresholdTuningTest {

    @Test
    fun `should_confirmDefaultsMaximiseAgreement_when_sweepingConfig`() {
        val corpus = CalibrationCorpus.load()
        val baseline = CalibrationCorpus.agreement(corpus, EvaluationConfig.DEFAULT)

        println("=== THRESHOLD TUNING SWEEP (Phase 5.2) ===")
        println("DEFAULT: cor=%.3f com=%.3f prn=%.3f pass=%.1f%%".format(
            baseline.correctnessCorr, baseline.completenessCorr,
            baseline.pronunciationCorr, baseline.passAgreement * 100))

        // Sweep the correctness-shaping penalties; report each point's correctness
        // agreement and pass agreement. The default (wrong=1.0, swapped=1.5,
        // extra=0.5, inversion=0.5) is the value the engine ships.
        var bestCorr = baseline.correctnessCorr
        for (wrong in PENALTY_GRID) {
            for (extra in PENALTY_GRID) {
                val cfg = EvaluationConfig.DEFAULT.copy(wrongWordPenalty = wrong, extraWordPenalty = extra)
                val ag = CalibrationCorpus.agreement(corpus, cfg)
                if (ag.correctnessCorr > bestCorr + EPS) bestCorr = ag.correctnessCorr
                println("wrong=%.2f extra=%.2f -> cor=%.3f pass=%.1f%%".format(
                    wrong, extra, ag.correctnessCorr, ag.passAgreement * 100))
            }
        }

        // Sweep the pass threshold and report pass/fail agreement so the sensitivity
        // default (0.85) is shown to maximise agreement on the corpus.
        println("--- passThreshold sweep ---")
        var bestPass = baseline.passAgreement
        for (pt in PASS_GRID) {
            val ag = CalibrationCorpus.agreement(corpus, EvaluationConfig.DEFAULT.copy(passThreshold = pt))
            if (ag.passAgreement > bestPass + EPS) bestPass = ag.passAgreement
            println("passThreshold=%.2f -> pass=%.1f%% (fp=%d fn=%d)".format(
                pt, ag.passAgreement * 100, ag.falsePass, ag.falseFail))
        }
        println("==========================================")

        // The shipped defaults must be optimal (no swept neighbour beats them).
        assertTrue(
            baseline.correctnessCorr >= bestCorr - EPS,
            "A swept penalty config beat the default correctness agreement (${"%.3f".format(bestCorr)} > ${"%.3f".format(baseline.correctnessCorr)}); update EvaluationConfig.DEFAULT"
        )
        assertTrue(
            baseline.passAgreement >= bestPass - EPS,
            "A swept passThreshold beat the default pass agreement; update the default"
        )
    }

    private companion object {
        val PENALTY_GRID = listOf(0.5f, 1.0f, 1.5f, 2.0f)
        val PASS_GRID = listOf(0.75f, 0.80f, 0.85f, 0.90f, 0.95f)
        const val EPS = 1e-6
    }
}
