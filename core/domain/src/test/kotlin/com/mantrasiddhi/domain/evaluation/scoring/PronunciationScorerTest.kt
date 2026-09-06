/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.scoring

import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
import com.mantrasiddhi.domain.evaluation.EvaluationConfig
import com.mantrasiddhi.domain.evaluation.WordFeedback
import com.mantrasiddhi.domain.evaluation.WordResult
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAssessment
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticWordScore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [PronunciationScorer]: aggregates only aligned words and weights them by
 * importance (syllable count), so missing words don't drag pronunciation down and a
 * fumbled long word matters more than a fumbled short one.
 */
class PronunciationScorerTest {

    @Test
    fun `should_scoreOne_when_allAlignedPerfectly`() {
        val words = listOf(
            WordResult("om", "om", 1.0f, null),
            WordResult("bhargo", "bhargo", 1.0f, null)
        )
        assertEquals(1.0f, PronunciationScorer.score(gayatri, words), 1e-4f)
    }

    @Test
    fun `should_scoreZero_when_nothingAligned`() {
        val words = listOf(WordResult("om", null, 0.0f, WordFeedback.MISSING))
        assertEquals(0.0f, PronunciationScorer.score(gayatri, words), 1e-4f)
    }

    @Test
    fun `should_ignoreMissingWords_when_aggregating`() {
        // A missing word should not pull pronunciation below the recited words' quality.
        val words = listOf(
            WordResult("om", "om", 1.0f, null),
            WordResult("bhur", null, 0.0f, WordFeedback.MISSING)
        )
        assertEquals(1.0f, PronunciationScorer.score(gayatri, words), 1e-4f)
    }

    @Test
    fun `should_weightByImportance_when_aggregating`() {
        // prachodayat (4 syllables) vs yo (1 syllable): a poor long word should hurt
        // more than a poor short word.
        val poorLong = listOf(
            WordResult("yo", "yo", 1.0f, null),
            WordResult("prachodayat", "x", 0.0f, WordFeedback.PRONUNCIATION)
        )
        val poorShort = listOf(
            WordResult("yo", "x", 0.0f, WordFeedback.PRONUNCIATION),
            WordResult("prachodayat", "prachodayat", 1.0f, null)
        )
        assertTrue(PronunciationScorer.score(gayatri, poorLong) < PronunciationScorer.score(gayatri, poorShort))
    }

    // ---- Phase 3: acoustic fusion ----

    private fun acoustic(vararg scores: Pair<Int, Float?>): AcousticAssessment =
        AcousticAssessment(
            wordScores = scores.map { AcousticWordScore(it.first, it.second, pacingRatio = 1.0f) },
            assessed = true,
            snrDb = 20f
        )

    @Test
    fun `should_useTextOnly_when_acousticNull`() {
        val words = listOf(WordResult("om", "om", 0.8f, null))
        assertEquals(
            PronunciationScorer.score(gayatri, words),
            PronunciationScorer.score(gayatri, words, acoustic = null, config = EvaluationConfig.DEFAULT),
            1e-4f
        )
    }

    @Test
    fun `should_detectBadPronunciation_when_acousticLowButTextMatches`() {
        // om's index in the reference is 0. Text scored it a perfect 1.0 (STT printed
        // the right word) but the audio did not match the benchmark (acoustic 0.2).
        val words = listOf(WordResult("om", "om", 1.0f, null))
        val fused = PronunciationScorer.score(gayatri, words, acoustic(0 to 0.2f), EvaluationConfig.DEFAULT)
        val textOnly = PronunciationScorer.score(gayatri, words)

        assertTrue(fused < textOnly, "acoustic must pull down a text-perfect but mispronounced word")
        // With default acousticWeight 0.7: 0.7*0.2 + 0.3*1.0 = 0.44.
        assertEquals(0.44f, fused, 0.02f)
    }

    @Test
    fun `should_fallBackToText_when_acousticAssessmentNotAssessed`() {
        val words = listOf(WordResult("om", "om", 0.9f, null))
        val notAssessed = AcousticAssessment.notAssessed(snrDb = 1f)
        assertEquals(
            PronunciationScorer.score(gayatri, words),
            PronunciationScorer.score(gayatri, words, notAssessed, EvaluationConfig.DEFAULT),
            1e-4f
        )
    }

    @Test
    fun `should_fallBackToText_perWord_when_acousticMissingForThatWord`() {
        // Acoustic assessed overall, but no score for om -> use its text score.
        val words = listOf(WordResult("om", "om", 0.6f, null))
        val fused = PronunciationScorer.score(gayatri, words, acoustic(0 to null), EvaluationConfig.DEFAULT)
        assertEquals(0.6f, fused, 1e-4f)
    }
}
