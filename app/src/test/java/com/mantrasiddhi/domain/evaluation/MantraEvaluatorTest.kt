/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MantraEvaluatorTest {

    @Test
    fun `should_scorePerfectRecitation_when_exactMantraSpoken`() {
        val input = "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val evaluation = MantraEvaluator.evaluate(input, 5000L)

        assertEquals(1.0f, evaluation.overallScore, 0.001f)
        assertTrue(evaluation.isCorrect)
        assertTrue(evaluation.missingWords.isEmpty())
        assertTrue(evaluation.extraWords.isEmpty())
        
        evaluation.wordResults.forEach { result ->
            assertEquals(1.0f, result.score, 0.001f)
            assertNull(result.feedback)
        }
    }

    @Test
    fun `should_scorePerfectRecitation_when_exactDevanagariSpoken`() {
        val input = "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात्"
        val evaluation = MantraEvaluator.evaluate(input, 5000L)

        assertEquals(1.0f, evaluation.overallScore, 0.001f)
        assertTrue(evaluation.isCorrect)
        assertTrue(evaluation.missingWords.isEmpty())
    }

    @Test
    fun `should_detectMissingWords_when_wordsSkipped`() {
        // Skipping "bhuvah" and "devasya"
        val input = "om bhur svah tat savitur varenyam bhargo dhimahi dhiyo yo nah prachodayat"
        val evaluation = MantraEvaluator.evaluate(input, 4000L)

        assertTrue(evaluation.missingWords.contains("bhuvah"))
        assertTrue(evaluation.missingWords.contains("devasya"))
        assertEquals(2, evaluation.missingWords.size)
        
        val bhuvahResult = evaluation.wordResults.first { it.referenceWord == "bhuvah" }
        assertEquals(0.0f, bhuvahResult.score)
        assertEquals(WordFeedback.MISSING, bhuvahResult.feedback)
    }

    @Test
    fun `should_detectExtraWords_when_unrelatedWordsAdded`() {
        val input = "om bhur bhuvah svah extra tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat word"
        val evaluation = MantraEvaluator.evaluate(input, 6000L)

        assertEquals(2, evaluation.extraWords.size)
        assertTrue(evaluation.extraWords.contains("extra"))
        assertTrue(evaluation.extraWords.contains("word"))
        assertTrue(evaluation.isCorrect) // Extra words are ignored in wordResults score
    }

    @Test
    fun `should_flagSwappedWords_when_wordOrderIncorrect`() {
        // Swapping "bhur" and "bhuvah"
        val input = "om bhuvah bhur svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val evaluation = MantraEvaluator.evaluate(input, 5000L)

        val bhurResult = evaluation.wordResults.first { it.referenceWord == "bhur" }
        val bhuvahResult = evaluation.wordResults.first { it.referenceWord == "bhuvah" }

        // Since they were swapped, they won't align perfectly at their original positions
        // and should have swap feedback if they match other words in the reference.
        assertEquals(WordFeedback.SWAPPED, bhurResult.feedback)
        assertEquals(WordFeedback.SWAPPED, bhuvahResult.feedback)
    }

    @Test
    fun `should_generatePronunciationFeedback_when_wordMispronounced`() {
        // "bhargo" replaced with "xyz" (completely different)
        val input = "om bhur bhuvah svah tat savitur varenyam xyz devasya dhimahi dhiyo yo nah prachodayat"
        val evaluation = MantraEvaluator.evaluate(input, 5000L)

        val bhargoResult = evaluation.wordResults.first { it.referenceWord == "bhargo" }
        assertEquals(WordFeedback.PRONUNCIATION, bhargoResult.feedback)
        assertTrue(bhargoResult.score < 0.4f)
    }

    @Test
    fun `should_generatePartialFeedback_when_wordClose`() {
        // "savitur" replaced with "savi" (close match)
        val input = "om bhur bhuvah svah tat savi varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val evaluation = MantraEvaluator.evaluate(input, 5000L)

        val saviturResult = evaluation.wordResults.first { it.referenceWord == "savitur" }
        assertEquals(WordFeedback.PARTIAL, saviturResult.feedback)
        assertTrue(saviturResult.score >= 0.4f && saviturResult.score < 0.70f)
    }

    @Test
    fun `should_evaluateAccurately_when_variousSingleWordErrorsTested`() {
        // 40+ variations to verify correct/incorrect classification
        // We will run a loop of 40 test cases to cover different speech outputs
        val evaluationTestCases = listOf(
            // 1-10: 1 word minor errors (should be correct)
            "om bhur bhuvah svah tat savitu varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,
            "om bhur bhuvah svah tat savitur varenya bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,
            "om bhur bhuvah svah tat savitur varenyam bhargo devasy dhimahi dhiyo yo nah prachodayat" to true,
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimah dhiyo yo nah prachodayat" to true,
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiy yo nah prachodayat" to true,
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo na prachodayat" to true,
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodaya" to true,
            "o bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,
            "om bhu bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,
            "om bhur bhuva svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,

            // 11-20: 1 word missing/wrong (should be correct as overallScore >= 0.85)
            // 1 missing word = 13/14 = 0.928 >= 0.85 (correct)
            "bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing om
            "om bhur bhuvah svah tat savitur varenyam devasya dhimahi dhiyo yo nah prachodayat" to true, // missing bhargo
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah" to true, // missing prachodayat
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo prachodayat" to true, // missing nah
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiyo nah prachodayat" to true, // missing yo
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi yo nah prachodayat" to true, // missing dhiyo
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhiyo yo nah prachodayat" to true, // missing dhimahi
            "om bhur bhuvah svah tat savitur varenyam bhargo dhimahi dhiyo yo nah prachodayat" to true, // missing devasya
            "om bhur bhuvah svah tat savitur bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing varenyam
            "om bhur bhuvah svah varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing tat/savitur (actually 2 missing words: 12/14 = 0.857 -> true!)

            // 21-30: 2 words missing/wrong (around threshold 12/14 = 0.857 -> correct)
            "bhur bhuvah svah tat savitur varenyam devasya dhimahi dhiyo yo nah prachodayat" to true, // missing om, bhargo
            "om bhur bhuvah svah tat savitur varenyam bhargo dhimahi dhiyo yo nah" to true, // missing devasya, prachodayat
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhiyo yo" to false, // missing dhimahi, nah, prachodayat -> wait, 3 missing: 11/14 = 0.785 < 0.85 -> false!
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi yo prachodayat" to true, // missing dhiyo, nah (12/14 = 0.857 -> correct)
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhiyo nah prachodayat" to true, // missing dhimahi, yo (12/14 = 0.857 -> correct)
            "om bhur bhuvah svah tat savitur varenyam bhargo dhimahi yo nah prachodayat" to true, // missing devasya, dhiyo (12/14 = 0.857 -> correct)
            "om bhur bhuvah svah tat savitur varenyam devasya dhimahi yo nah prachodayat" to true, // missing bhargo, dhiyo (12/14 = 0.857 -> correct)
            "om bhur bhuvah svah tat varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing savitur
            "om bhur bhuvah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing svah
            "om bhur svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing bhuvah

            // 31-40: 3+ words missing (should be incorrect, overallScore < 0.85)
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi" to false, // missing last 4 words (10/14 = 0.714)
            "devasya dhimahi dhiyo yo nah prachodayat" to false, // missing first 8 words
            "om bhur bhuvah svah tat savitur" to false,
            "om bhur bhuvah svah" to false,
            "tat savitur varenyam bhargo" to false,
            "dhimahi dhiyo yo nah prachodayat" to false,
            "om bhur bhuvah svah tat savitur varenyam bhargo devasya" to false,
            "om tat bhargo dhiyo prachodayat" to false,
            "svah varenyam dhimahi prachodayat" to false,
            "om bhur bhuvah xyz abc def bhargo devasya dhimahi dhiyo yo nah prachodayat" to false, // 3 wrong words

            // 41-43: Empty/totally wrong inputs
            "" to false,
            "completely unrelated text spoken here" to false,
            "har har mahadev" to false
        )

        evaluationTestCases.forEachIndexed { index, (input, expectedIsCorrect) ->
            val evaluation = MantraEvaluator.evaluate(input, 5000L)
            assertEquals(
                expectedIsCorrect,
                evaluation.isCorrect,
                "Test case ${index + 1} failed for input: '$input'. Score: ${evaluation.overallScore}"
            )
        }
    }
}
