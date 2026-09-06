package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EvaluationPerformanceTest {
    private val full = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"

    @Test
    fun `cached hypothesis assessments preserve every field of the original selection path`() {
        val cases = listOf(
            listOf(full to 0.9f),
            listOf("om bhur" to 1f, full to 0.2f, full to 0.8f),
            listOf("om bhur" to 0.3f, "om bhur" to 0.7f),
            listOf("" to 1f),
            emptyList()
        )
        for (alternatives in cases) {
            val chosen = HypothesisSelector.select(alternatives) {
                MantraEvaluator.assess(gayatri, it, 12000).overall.score
            }
            val expected = MantraEvaluator.assess(gayatri, chosen?.first.orEmpty(), 12000,
                confidence = chosen?.second ?: 0f)
            assertEquals(expected, MantraEvaluator.assessBest(gayatri, alternatives, 12000))
        }
    }

    @Test
    fun `report warm text evaluation latency without imposing a host dependent threshold`() {
        val hypotheses = listOf("om bhur" to 0.9f, full to 0.8f, full to 0.7f)
        repeat(30) { MantraEvaluator.assessBest(gayatri, hypotheses, 12000) }
        val durations = List(100) {
            val start = System.nanoTime()
            MantraEvaluator.assessBest(gayatri, hypotheses, 12000)
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        println("Text evaluator, 3 hypotheses, 100 warm JVM runs: median=${durations[50]}ms p95=${durations[95]}ms")
    }
}
