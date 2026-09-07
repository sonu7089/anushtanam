/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.scoring

import com.mantrasiddhi.domain.evaluation.WordFeedback
import com.mantrasiddhi.domain.evaluation.WordResult
import com.mantrasiddhi.domain.evaluation.model.PadaResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [CompletenessScorer]: coverage of words/padas, with a heavier penalty
 * for losing a whole line than for scattered words.
 */
class CompletenessScorerTest {

    private fun recited(word: String) = WordResult(word, word, 1.0f, null)
    private fun missing(word: String) = WordResult(word, null, 0.0f, WordFeedback.MISSING)
    private fun pada(pada: Int, words: List<WordResult>) =
        PadaResult(pada, words.count { it.spokenWord != null }.toFloat() / words.size, 1.0f, words)

    @Test
    fun `should_scoreOne_when_allRecited`() {
        val words = (1..4).map { recited("w$it") }
        val padas = listOf(pada(1, words))
        assertEquals(1.0f, CompletenessScorer.score(words, padas, wholePadaPenalty = 0.05f), 1e-4f)
    }

    @Test
    fun `should_scoreZero_when_emptyResults`() {
        assertEquals(0.0f, CompletenessScorer.score(emptyList(), emptyList(), 0.05f), 1e-4f)
    }

    @Test
    fun `should_penalizeWholePada_moreThanScatteredWords`() {
        // Both scenarios drop exactly three of six words; only the placement differs,
        // so any score gap is purely the whole-pada penalty.
        val scattered = listOf(
            recited("a"), missing("b"), recited("c"),
            missing("d"), recited("e"), missing("f")
        )
        val scatteredPadas = listOf(
            pada(1, scattered.subList(0, 3)),
            pada(2, scattered.subList(3, 6))
        )
        val wholeLine = listOf(
            missing("a"), missing("b"), missing("c"),
            recited("d"), recited("e"), recited("f")
        )
        val wholeLinePadas = listOf(
            pada(1, wholeLine.subList(0, 3)),
            pada(2, wholeLine.subList(3, 6))
        )

        val scatteredScore = CompletenessScorer.score(scattered, scatteredPadas, 0.05f)
        val wholeLineScore = CompletenessScorer.score(wholeLine, wholeLinePadas, 0.05f)
        assertTrue(wholeLineScore < scatteredScore, "whole=$wholeLineScore scattered=$scatteredScore")
    }
}
