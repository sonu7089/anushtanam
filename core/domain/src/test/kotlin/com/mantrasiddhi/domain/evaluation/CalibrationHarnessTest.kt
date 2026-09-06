/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Calibration **regression gate** (Phase 5.3).
 *
 * Phase 1 shipped this harness in report-only mode. It is now a hard CI gate: the
 * build fails if the evaluation engine's agreement with the human-labeled golden
 * corpus ([CalibrationCorpus]) drops below the locked thresholds, or if the text
 * path blows the latency budget.
 *
 * The locked thresholds are the Phase-5.2 acceptance targets; the engine clears them
 * with margin on the tuned defaults (see [GATE_CORRECTNESS_CORR] etc. for the
 * measured values). [should_failGate_when_engineRegresses] proves the gate is
 * actually sensitive — a deliberately crippled engine breaches it.
 */
class CalibrationHarnessTest {

    @Test
    fun `should_meetLockedAgreementThresholds_when_runOverGoldenCorpus`() {
        val corpus = CalibrationCorpus.load()
        assertTrue(corpus.isNotEmpty(), "Calibration corpus must not be empty")
        assertCorpusCoverage(corpus)

        val ag = CalibrationCorpus.agreement(corpus)
        printReport(corpus, ag)

        assertTrue(
            ag.correctnessCorr >= GATE_CORRECTNESS_CORR,
            "Correctness rank-correlation ${"%.3f".format(ag.correctnessCorr)} fell below the locked gate $GATE_CORRECTNESS_CORR"
        )
        assertTrue(
            ag.completenessCorr >= GATE_COMPLETENESS_CORR,
            "Completeness rank-correlation ${"%.3f".format(ag.completenessCorr)} fell below the locked gate $GATE_COMPLETENESS_CORR"
        )
        assertTrue(
            ag.pronunciationCorr >= GATE_PRONUNCIATION_CORR,
            "Pronunciation (text-path) rank-correlation ${"%.3f".format(ag.pronunciationCorr)} fell below the locked gate $GATE_PRONUNCIATION_CORR"
        )
        assertTrue(
            ag.passAgreement >= GATE_PASS_AGREEMENT,
            "Pass/fail agreement ${"%.1f".format(ag.passAgreement * 100)}% fell below the locked gate ${GATE_PASS_AGREEMENT * 100}%"
        )
    }

    @Test
    fun `should_keepTextPathWithinLatencyBudget_when_assessing`() {
        val corpus = CalibrationCorpus.load()
        // Warm up the JIT so the measurement reflects steady-state, not class-load cost.
        repeat(WARMUP_ITERATIONS) { corpus.forEach { MantraEvaluator.assess(gayatri, it.transcription, 0L) } }

        var maxNanos = 0L
        repeat(TIMED_ITERATIONS) {
            corpus.forEach { case ->
                val start = System.nanoTime()
                MantraEvaluator.assess(gayatri, case.transcription, 0L)
                maxNanos = maxOf(maxNanos, System.nanoTime() - start)
            }
        }
        val maxMs = maxNanos / 1_000_000.0
        println("Max text-path assess latency: %.3f ms (budget %d ms)".format(maxMs, TEXT_LATENCY_BUDGET_MS))
        assertTrue(
            maxMs < TEXT_LATENCY_BUDGET_MS,
            "Text-path evaluation took %.3f ms, exceeding the %d ms budget".format(maxMs, TEXT_LATENCY_BUDGET_MS)
        )
        // The hybrid (text + acoustic DTW) < 300 ms budget is exercised where audio
        // features are available; the acoustic DTW component is timed in DtwTest /
        // AcousticPronunciationScorerTest. Headless CI has no microphone, so only the
        // text path is gated here.
    }

    @Test
    fun `should_failGate_when_engineRegresses`() {
        // Models a regression (e.g. reverting the phonetic alignment to raw
        // char-Levenshtein, or dropping the sequence penalties): an engine that no
        // longer penalises wrong/extra/swapped words collapses correctness ordering.
        val crippled = EvaluationConfig.DEFAULT.copy(
            wrongWordPenalty = 0f,
            extraWordPenalty = 0f,
            swappedWordPenalty = 0f,
            inversionPenalty = 0f,
            matchThreshold = 0f
        )
        val ag = CalibrationCorpus.agreement(CalibrationCorpus.load(), crippled)
        assertTrue(
            ag.correctnessCorr < GATE_CORRECTNESS_CORR,
            "A crippled engine should breach the correctness gate, but scored ${"%.3f".format(ag.correctnessCorr)}"
        )
    }

    private fun assertCorpusCoverage(corpus: List<CalibrationCase>) {
        val classes = corpus.map { it.errorClass }.toSet()
        REQUIRED_ERROR_CLASSES.forEach { required ->
            assertTrue(
                required in classes,
                "Corpus must cover the '$required' error class (Phase 5.1 acceptance criterion)"
            )
        }
    }

    private fun printReport(corpus: List<CalibrationCase>, ag: CalibrationCorpus.Agreement) {
        println("=== CALIBRATION GATE (Phase 5) ===")
        println("Corpus size: ${corpus.size} (text-pass cases: ${ag.truePass + ag.trueFail + ag.falsePass + ag.falseFail})")
        println("Correctness   rank-correlation: %.3f (gate >= %.2f)".format(ag.correctnessCorr, GATE_CORRECTNESS_CORR))
        println("Completeness  rank-correlation: %.3f (gate >= %.2f)".format(ag.completenessCorr, GATE_COMPLETENESS_CORR))
        println("Pronunciation rank-correlation: %.3f (gate >= %.2f, text-assessable only)".format(ag.pronunciationCorr, GATE_PRONUNCIATION_CORR))
        println("Pass/fail agreement: %.1f%% (gate >= %.0f%%)".format(ag.passAgreement * 100, GATE_PASS_AGREEMENT * 100))
        println("Confusion (pass): tp=%d tn=%d fp=%d fn=%d".format(ag.truePass, ag.trueFail, ag.falsePass, ag.falseFail))
        if (ag.disagreements.isEmpty()) {
            println("No pass/fail disagreements.")
        } else {
            println("Worst disagreements:")
            ag.disagreements.forEach { println("  ${it.caseId}: enginePass=${it.enginePass} humanPass=${it.humanPass}") }
        }
        println("==================================")
    }

    companion object {
        // Locked Phase-5.2 acceptance thresholds. Measured agreement on the tuned
        // EvaluationConfig.DEFAULT over the golden corpus (clears each with margin):
        //   correctness  0.934  |  completeness 0.997  |  pronunciation(text) 0.949  |  pass 100%
        private const val GATE_CORRECTNESS_CORR = 0.85
        private const val GATE_COMPLETENESS_CORR = 0.85
        private const val GATE_PRONUNCIATION_CORR = 0.85
        private const val GATE_PASS_AGREEMENT = 0.80

        /** Text-path latency budget (§ README 6 / phase-3 §3.5). */
        private const val TEXT_LATENCY_BUDGET_MS = 50

        private const val WARMUP_ITERATIONS = 50
        private const val TIMED_ITERATIONS = 50

        /** Error classes the corpus must cover (Phase 5.1). */
        private val REQUIRED_ERROR_CLASSES = listOf(
            "clean", "missing-word", "missing-pada", "swap", "substitution",
            "extra", "de-aspiration", "retroflex", "vowel-length", "visarga", "gibberish"
        )
    }
}
