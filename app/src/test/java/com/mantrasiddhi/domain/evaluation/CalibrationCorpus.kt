/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import org.json.JSONArray
import kotlin.math.sqrt

/**
 * Shared helpers for the calibration tests: loading the golden corpus and computing
 * the agreement statistics (rank correlation, pass/fail agreement, confusion matrix)
 * used by both [CalibrationHarnessTest] (the CI gate) and [ThresholdTuningTest] (the
 * config sweep). Centralising them keeps the gate and the tuner measuring identically.
 */
object CalibrationCorpus {

    /** Loads every [CalibrationCase] from the test-classpath corpus resource. */
    fun load(): List<CalibrationCase> {
        val stream = CalibrationCorpus::class.java.getResourceAsStream("/eval/corpus.json")
            ?: error("Calibration corpus /eval/corpus.json not found on the test classpath")
        val json = stream.bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            CalibrationCase(
                id = obj.getString("id"),
                transcription = obj.getString("transcription"),
                errorClass = if (obj.has("errorClass")) obj.getString("errorClass") else "unspecified",
                audioAsset = if (obj.isNull("audioAsset")) null else obj.getString("audioAsset"),
                humanCorrectness = obj.getDouble("humanCorrectness").toFloat(),
                humanCompleteness = obj.getDouble("humanCompleteness").toFloat(),
                humanPronunciation = obj.getDouble("humanPronunciation").toFloat(),
                expectedPass = obj.getBoolean("expectedPass"),
                pronunciationNeedsAudio = obj.has("pronunciationNeedsAudio") &&
                    obj.getBoolean("pronunciationNeedsAudio")
            )
        }
    }

    /**
     * Runs the (text-path) engine over [corpus] with [config] and returns the
     * per-axis agreement against the human labels.
     *
     * Pronunciation correlation is measured only over the cases the **text** path can
     * fairly judge (`!pronunciationNeedsAudio`); pass/fail agreement likewise excludes
     * audio-only cases, whose verdict hinges on a signal the CI harness does not run.
     */
    fun agreement(
        corpus: List<CalibrationCase>,
        config: EvaluationConfig = EvaluationConfig.DEFAULT
    ): Agreement {
        val eCor = mutableListOf<Float>()
        val hCor = mutableListOf<Float>()
        val eCom = mutableListOf<Float>()
        val hCom = mutableListOf<Float>()
        val ePrn = mutableListOf<Float>()
        val hPrn = mutableListOf<Float>()

        var tp = 0
        var tn = 0
        var fp = 0
        var fn = 0
        var passTotal = 0
        val disagreements = mutableListOf<Disagreement>()

        corpus.forEach { case ->
            val a = MantraEvaluator.assess(case.transcription, recitationTimeMs = 0L, config = config)
            eCor += a.correctness.score; hCor += case.humanCorrectness
            eCom += a.completeness.score; hCom += case.humanCompleteness
            if (!case.pronunciationNeedsAudio) {
                ePrn += a.pronunciation.score; hPrn += case.humanPronunciation
            }
            if (!case.pronunciationNeedsAudio) {
                passTotal++
                when {
                    a.isPass && case.expectedPass -> tp++
                    !a.isPass && !case.expectedPass -> tn++
                    a.isPass && !case.expectedPass -> { fp++; disagreements += Disagreement(case.id, "pass", true, false) }
                    else -> { fn++; disagreements += Disagreement(case.id, "pass", false, true) }
                }
            }
        }

        val agreementCount = tp + tn
        return Agreement(
            correctnessCorr = spearman(eCor, hCor),
            completenessCorr = spearman(eCom, hCom),
            pronunciationCorr = spearman(ePrn, hPrn),
            passAgreement = if (passTotal == 0) 0.0 else agreementCount.toDouble() / passTotal,
            truePass = tp,
            trueFail = tn,
            falsePass = fp,
            falseFail = fn,
            disagreements = disagreements
        )
    }

    /** Spearman rank correlation between two equal-length series (0.0 if undefined). */
    fun spearman(a: List<Float>, b: List<Float>): Double {
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

    private fun ranks(values: List<Float>): DoubleArray {
        val indexed = values.withIndex().sortedBy { it.value }
        val result = DoubleArray(values.size)
        var i = 0
        while (i < indexed.size) {
            var j = i
            while (j + 1 < indexed.size && indexed[j + 1].value == indexed[i].value) j++
            val rank = (i + j) / 2.0 + 1.0
            for (k in i..j) result[indexed[k].index] = rank
            i = j + 1
        }
        return result
    }

    /** Aggregate agreement statistics for one engine configuration over the corpus. */
    data class Agreement(
        val correctnessCorr: Double,
        val completenessCorr: Double,
        val pronunciationCorr: Double,
        val passAgreement: Double,
        val truePass: Int,
        val trueFail: Int,
        val falsePass: Int,
        val falseFail: Int,
        val disagreements: List<Disagreement>
    )

    /** A single engine-vs-human disagreement, for the worst-disagreements report. */
    data class Disagreement(
        val caseId: String,
        val axis: String,
        val enginePass: Boolean,
        val humanPass: Boolean
    )
}
