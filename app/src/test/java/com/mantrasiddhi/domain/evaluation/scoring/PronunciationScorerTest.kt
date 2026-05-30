/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.scoring

import com.mantrasiddhi.domain.evaluation.WordFeedback
import com.mantrasiddhi.domain.evaluation.WordResult
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
        assertEquals(1.0f, PronunciationScorer.score(words), 1e-4f)
    }

    @Test
    fun `should_scoreZero_when_nothingAligned`() {
        val words = listOf(WordResult("om", null, 0.0f, WordFeedback.MISSING))
        assertEquals(0.0f, PronunciationScorer.score(words), 1e-4f)
    }

    @Test
    fun `should_ignoreMissingWords_when_aggregating`() {
        // A missing word should not pull pronunciation below the recited words' quality.
        val words = listOf(
            WordResult("om", "om", 1.0f, null),
            WordResult("bhur", null, 0.0f, WordFeedback.MISSING)
        )
        assertEquals(1.0f, PronunciationScorer.score(words), 1e-4f)
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
        assertTrue(PronunciationScorer.score(poorLong) < PronunciationScorer.score(poorShort))
    }
}
