/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [SwaraScorer]: that a matching pitch contour scores high, that a flat or
 * wrong-direction recitation is both scored lower and given the right coaching hint
 * (the discrimination the Phase-5.5 acceptance criterion requires), and that it
 * degrades to "not assessed" when there is too little voiced pitch.
 */
class SwaraScorerTest {

    private val scale = 4.0
    private val minVoiced = 5
    private val flatThreshold = 1.0f

    private fun rising(from: Float, to: Float, n: Int) =
        FloatArray(n) { from + (to - from) * it / (n - 1) }

    private fun falling(from: Float, to: Float, n: Int) = rising(to, from, n)

    private fun constant(value: Float, n: Int) = FloatArray(n) { value }

    private fun score(learner: FloatArray?, benchmark: FloatArray) =
        SwaraScorer.score(listOf(learner), listOf(benchmark), scale, minVoiced, flatThreshold)

    @Test
    fun `should_scoreHighAndGood_when_contourMatchesBenchmark`() {
        val benchmark = rising(200f, 260f, 12)
        val result = score(benchmark.copyOf(), benchmark)
        assertTrue(result.assessed)
        assertTrue(result.overallScore > 0.9f, "identical contour should score ~1.0 (was ${result.overallScore})")
        assertEquals(SwaraAssessment.Hint.GOOD, result.hint)
    }

    @Test
    fun `should_scoreLowerAndFlat_when_recitationIsMonotone`() {
        val benchmark = rising(200f, 280f, 12)
        val matching = score(benchmark.copyOf(), benchmark)
        val flat = score(constant(220f, 12), benchmark)
        assertTrue(flat.assessed)
        assertTrue(flat.overallScore < matching.overallScore, "flat must score below a matching contour")
        assertEquals(SwaraAssessment.Hint.FLAT, flat.hint)
    }

    @Test
    fun `should_flagWrongDirection_when_pitchMovesOppositely`() {
        val benchmark = rising(200f, 280f, 12)
        val inverted = score(falling(200f, 280f, 12), benchmark)
        assertTrue(inverted.assessed)
        assertEquals(SwaraAssessment.Hint.WRONG_DIRECTION, inverted.hint)
        assertTrue(inverted.overallScore < 0.75f)
    }

    @Test
    fun `should_discriminateCorrectFromIncorrectPitch`() {
        val benchmark = rising(200f, 280f, 12)
        val correct = score(benchmark.copyOf(), benchmark).overallScore
        val flat = score(constant(220f, 12), benchmark).overallScore
        val wrong = score(falling(200f, 280f, 12), benchmark).overallScore
        assertTrue(correct > flat && correct > wrong, "correct ($correct) must beat flat ($flat) and wrong ($wrong)")
    }

    @Test
    fun `should_returnNotAssessed_when_tooFewVoicedFrames`() {
        val result = score(constant(0f, 12), rising(200f, 260f, 12)) // learner all unvoiced
        assertFalse(result.assessed)
    }

    @Test
    fun `should_returnNotAssessed_when_noWords`() {
        val result = SwaraScorer.score(emptyList(), emptyList(), scale, minVoiced, flatThreshold)
        assertFalse(result.assessed)
        assertEquals(0f, result.overallScore)
    }
}
