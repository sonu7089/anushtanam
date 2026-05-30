/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import org.json.JSONArray
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Calibration harness: runs the evaluation engine over a labeled golden corpus and
 * reports per-axis agreement with human judgment.
 *
 * Phase 1 scope: this test only *reports* a baseline (printed to stdout) and
 * asserts the harness itself runs end-to-end. Phase 5 turns the agreement metrics
 * into a CI gate.
 */
class CalibrationHarnessTest {

    @Test
    fun `should_reportPerAxisAgreementBaseline_when_runOverCorpus`() {
        val corpus = loadCorpus()
        assertTrue(corpus.isNotEmpty(), "Calibration corpus must not be empty")

        val engineCorrectness = mutableListOf<Float>()
        val engineCompleteness = mutableListOf<Float>()
        val enginePronunciation = mutableListOf<Float>()
        val humanCorrectness = mutableListOf<Float>()
        val humanCompleteness = mutableListOf<Float>()
        val humanPronunciation = mutableListOf<Float>()

        var passAgreementCount = 0

        println("=== CALIBRATION HARNESS BASELINE (Phase 1) ===")
        println(
            "%-26s | %-5s %-5s | %-5s %-5s | %-5s %-5s | %-4s %-4s"
                .format("id", "eCor", "hCor", "eCom", "hCom", "ePrn", "hPrn", "eP", "hP")
        )

        corpus.forEach { case ->
            val assessment = MantraEvaluator.assess(case.transcription, recitationTimeMs = 0L)

            engineCorrectness += assessment.correctness.score
            engineCompleteness += assessment.completeness.score
            enginePronunciation += assessment.pronunciation.score
            humanCorrectness += case.humanCorrectness
            humanCompleteness += case.humanCompleteness
            humanPronunciation += case.humanPronunciation

            if (assessment.isPass == case.expectedPass) passAgreementCount++

            println(
                "%-26s | %-5.2f %-5.2f | %-5.2f %-5.2f | %-5.2f %-5.2f | %-4s %-4s".format(
                    case.id,
                    assessment.correctness.score, case.humanCorrectness,
                    assessment.completeness.score, case.humanCompleteness,
                    assessment.pronunciation.score, case.humanPronunciation,
                    assessment.isPass, case.expectedPass
                )
            )
        }

        val correctnessCorr = spearman(engineCorrectness, humanCorrectness)
        val completenessCorr = spearman(engineCompleteness, humanCompleteness)
        val pronunciationCorr = spearman(enginePronunciation, humanPronunciation)
        val passAgreement = passAgreementCount.toDouble() / corpus.size

        println("--- Baseline agreement (current engine) ---")
        println("Correctness   rank-correlation: %.3f".format(correctnessCorr))
        println("Completeness  rank-correlation: %.3f".format(completenessCorr))
        println("Pronunciation rank-correlation: %.3f".format(pronunciationCorr))
        println("Pass/fail agreement: %.1f%% (%d/%d)".format(passAgreement * 100, passAgreementCount, corpus.size))
        println("===============================================")

        // Phase 1 only reports: assert the metrics are well-formed, not that they hit a target.
        assertTrue(passAgreement in 0.0..1.0)
        assertTrue(correctnessCorr in -1.0..1.0)
        assertTrue(completenessCorr in -1.0..1.0)
        assertTrue(pronunciationCorr in -1.0..1.0)
    }

    private fun loadCorpus(): List<CalibrationCase> {
        val stream = javaClass.getResourceAsStream("/eval/corpus.json")
            ?: error("Calibration corpus /eval/corpus.json not found on the test classpath")
        val json = stream.bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            CalibrationCase(
                id = obj.getString("id"),
                transcription = obj.getString("transcription"),
                audioAsset = if (obj.isNull("audioAsset")) null else obj.getString("audioAsset"),
                humanCorrectness = obj.getDouble("humanCorrectness").toFloat(),
                humanCompleteness = obj.getDouble("humanCompleteness").toFloat(),
                humanPronunciation = obj.getDouble("humanPronunciation").toFloat(),
                expectedPass = obj.getBoolean("expectedPass")
            )
        }
    }

    /**
     * Spearman rank correlation between two equal-length series. Returns 0.0 when
     * either series has no variance (undefined correlation), so the baseline report
     * stays well-formed.
     */
    private fun spearman(a: List<Float>, b: List<Float>): Double {
        require(a.size == b.size)
        if (a.size < 2) return 0.0
        val ra = ranks(a)
        val rb = ranks(b)
        val meanA = ra.average()
        val meanB = rb.average()
        var cov = 0.0
        var varA = 0.0
        var varB = 0.0
        for (i in ra.indices) {
            val da = ra[i] - meanA
            val db = rb[i] - meanB
            cov += da * db
            varA += da * da
            varB += db * db
        }
        if (varA == 0.0 || varB == 0.0) return 0.0
        return cov / sqrt(varA * varB)
    }

    /** Fractional (tie-averaged) ranks of the values. */
    private fun ranks(values: List<Float>): DoubleArray {
        val indexed = values.withIndex().sortedBy { it.value }
        val result = DoubleArray(values.size)
        var i = 0
        while (i < indexed.size) {
            var j = i
            while (j + 1 < indexed.size && indexed[j + 1].value == indexed[i].value) j++
            val rank = (i + j) / 2.0 + 1.0 // 1-based, averaged over ties
            for (k in i..j) result[indexed[k].index] = rank
            i = j + 1
        }
        return result
    }
}
