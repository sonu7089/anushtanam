/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
import com.mantrasiddhi.domain.evaluation.feedback.FeedbackFeature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImprovementSuggestionEngineTest {

    @Test
    fun should_returnNoTips_when_allWordsAccurate() {
        val results = listOf(
            WordResult(referenceWord = "om", spokenWord = "om", score = 1.0f, feedback = null),
            WordResult(referenceWord = "bhargo", spokenWord = "bhargo", score = 1.0f, feedback = null)
        )
        assertTrue(ImprovementSuggestionEngine.topTips(gayatri, results).isEmpty())
    }

    @Test
    fun should_returnContrastiveTipsForWeakWords_orderedWorstFirst() {
        val results = listOf(
            WordResult(referenceWord = "om", spokenWord = "om", score = 1.0f, feedback = null),
            WordResult(referenceWord = "bhargo", spokenWord = "bargo", score = 0.6f, feedback = WordFeedback.PRONUNCIATION),
            WordResult(referenceWord = "dhimahi", spokenWord = "dimahi", score = 0.4f, feedback = WordFeedback.PRONUNCIATION)
        )

        val tips = ImprovementSuggestionEngine.topTips(gayatri, results)

        assertEquals(2, tips.size)
        // Worst (lowest score) first.
        assertEquals("dhimahi", tips.first().word)
        assertTrue(tips.any { it.word == "bhargo" })
    }

    @Test
    fun should_nameSyllableAndFeature_when_aspirationDropped() {
        val tip = ImprovementSuggestionEngine.tipForWord("bhargo", "bargo")
        assertNotNull(tip)
        assertEquals(FeedbackFeature.DROPPED_ASPIRATION, tip!!.feature)
        assertEquals("bhar", tip.syllable)
        // The contrast hint splits out the breath so the fix is obvious.
        assertEquals("b-h-ar", tip.contrast)
    }

    @Test
    fun should_returnMissingWordTip_when_wordSkipped() {
        val tip = ImprovementSuggestionEngine.tipForWord("varenyam", null)
        assertNotNull(tip)
        assertEquals(FeedbackFeature.MISSING_WORD, tip!!.feature)
    }

    @Test
    fun should_returnNull_when_wordUnknown() {
        assertNull(ImprovementSuggestionEngine.tipForWord("notamantraword", "xyz"))
    }
}
