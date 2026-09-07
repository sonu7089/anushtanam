/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.scoring

import com.mantrasiddhi.domain.evaluation.AlignmentStep
import com.mantrasiddhi.domain.evaluation.EvaluationConfig
import com.mantrasiddhi.domain.evaluation.WordFeedback
import com.mantrasiddhi.domain.evaluation.WordResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [SequenceScorer] (the correctness axis): perfect order scores 1.0,
 * swaps and wrong words lower it, and missing words are deliberately ignored here.
 */
class SequenceScorerTest {

    private val config = EvaluationConfig.DEFAULT

    private fun ok(word: String) = WordResult(word, word, 1.0f, null)
    private fun monotonicAlignment(count: Int) =
        (0 until count).map { AlignmentStep(refIndex = it, spokenIndex = it) }

    @Test
    fun `should_scoreOne_when_perfectOrder`() {
        val words = (0 until 5).map { ok("w$it") }
        val score = SequenceScorer.score(words, monotonicAlignment(5), extraWordCount = 0, config)
        assertEquals(1.0f, score, 1e-4f)
    }

    @Test
    fun `should_ignoreMissingWords`() {
        // Missing words are a completeness concern; correctness should stay high.
        val words = listOf(ok("a"), WordResult("b", null, 0.0f, WordFeedback.MISSING), ok("c"))
        val alignment = listOf(AlignmentStep(0, 0), AlignmentStep(1, null), AlignmentStep(2, 1))
        val score = SequenceScorer.score(words, alignment, extraWordCount = 0, config)
        assertEquals(1.0f, score, 1e-4f)
    }

    @Test
    fun `should_penalizeSwaps`() {
        val words = listOf(
            ok("a"),
            WordResult("b", "c", 0.2f, WordFeedback.SWAPPED),
            WordResult("c", "b", 0.2f, WordFeedback.SWAPPED),
            ok("d")
        )
        val score = SequenceScorer.score(words, monotonicAlignment(4), extraWordCount = 0, config)
        assertTrue(score < 1.0f, "swaps must lower correctness")
    }

    @Test
    fun `should_penalizeExtraWords`() {
        val words = (0 until 4).map { ok("w$it") }
        val withExtras = SequenceScorer.score(words, monotonicAlignment(4), extraWordCount = 2, config)
        val withoutExtras = SequenceScorer.score(words, monotonicAlignment(4), extraWordCount = 0, config)
        assertTrue(withExtras < withoutExtras)
    }

    @Test
    fun `should_penalizeSwapsMoreThanEqualNumberOfExtras`() {
        val words = listOf(
            ok("a"),
            WordResult("b", "c", 0.2f, WordFeedback.SWAPPED),
            WordResult("c", "b", 0.2f, WordFeedback.SWAPPED),
            ok("d")
        )
        val cleanWords = (0 until 4).map { ok("w$it") }
        val swapScore = SequenceScorer.score(words, monotonicAlignment(4), extraWordCount = 0, config)
        val extraScore = SequenceScorer.score(cleanWords, monotonicAlignment(4), extraWordCount = 2, config)
        assertTrue(swapScore < extraScore, "two swaps should cost more than two stray words")
    }
}
