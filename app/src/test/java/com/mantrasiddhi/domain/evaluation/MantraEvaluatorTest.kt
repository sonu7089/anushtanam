/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.model.ScoreBand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MantraEvaluatorTest {

    @Test
    fun `should_scorePerfectRecitation_when_exactMantraSpoken`() {
        val input = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
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
        val input = "om bhur svah tatsavitur varenyam bhargo dhimahi dhiyo yo nah prachodayat"
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
        val input = "om bhur bhuvah svah extra tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat word"
        val evaluation = MantraEvaluator.evaluate(input, 6000L)

        assertEquals(2, evaluation.extraWords.size)
        assertTrue(evaluation.extraWords.contains("extra"))
        assertTrue(evaluation.extraWords.contains("word"))
        assertTrue(evaluation.isCorrect) // Extra words are ignored in wordResults score
    }

    @Test
    fun `should_flagSwappedWords_when_wordOrderIncorrect`() {
        // Swapping "bhur" and "bhuvah"
        val input = "om bhuvah bhur svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
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
        val input = "om bhur bhuvah svah tatsavitur varenyam xyz devasya dhimahi dhiyo yo nah prachodayat"
        val evaluation = MantraEvaluator.evaluate(input, 5000L)

        val bhargoResult = evaluation.wordResults.first { it.referenceWord == "bhargo" }
        assertEquals(WordFeedback.PRONUNCIATION, bhargoResult.feedback)
        assertTrue(bhargoResult.score < 0.4f)
    }

    @Test
    fun `should_generatePartialFeedback_when_wordClose`() {
        // "tatsavitur" replaced with "tatsav" (close match)
        val input = "om bhur bhuvah svah tatsav varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val evaluation = MantraEvaluator.evaluate(input, 5000L)

        val saviturResult = evaluation.wordResults.first { it.referenceWord == "tatsavitur" }
        assertEquals(WordFeedback.PARTIAL, saviturResult.feedback)
        assertTrue(saviturResult.score >= 0.4f && saviturResult.score < 0.70f)
    }

    @Test
    fun `should_evaluateAccurately_when_variousSingleWordErrorsTested`() {
        val evaluationTestCases = listOf(
            // 1-10: 1 word minor errors (should be correct)
            "om bhur bhuvah svah tatsavitu varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,
            "om bhur bhuvah svah tatsavitur varenya bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasy dhimahi dhiyo yo nah prachodayat" to true,
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimah dhiyo yo nah prachodayat" to true,
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiy yo nah prachodayat" to true,
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo na prachodayat" to true,
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodaya" to true,
            "o bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,
            "om bhu bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,
            "om bhur bhuva svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,

            // 11-20: 1 word missing/wrong (should be correct as overallScore >= 0.85)
            // 1 missing word = 12/13 = 0.923 >= 0.85 (correct)
            "bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing om
            "om bhur bhuvah svah tatsavitur varenyam devasya dhimahi dhiyo yo nah prachodayat" to true, // missing bhargo
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah" to true, // missing prachodayat
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo prachodayat" to true, // missing nah
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo nah prachodayat" to true, // missing yo
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi yo nah prachodayat" to true, // missing dhiyo
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhiyo yo nah prachodayat" to true, // missing dhimahi
            "om bhur bhuvah svah tatsavitur varenyam bhargo dhimahi dhiyo yo nah prachodayat" to true, // missing devasya
            "om bhur bhuvah svah tatsavitur bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing varenyam
            "om bhur bhuvah svah varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing tatsavitur

            // 21-30: 2 words missing/wrong (below threshold 11/13 = 0.846 < 0.85 -> incorrect)
            "bhur bhuvah svah tatsavitur varenyam devasya dhimahi dhiyo yo nah prachodayat" to false, // missing om, bhargo
            "om bhur bhuvah svah tatsavitur varenyam bhargo dhimahi dhiyo yo nah" to false, // missing devasya, prachodayat
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhiyo yo" to false, // missing dhimahi, nah, prachodayat
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi yo prachodayat" to false, // missing dhiyo, nah
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhiyo nah prachodayat" to false, // missing dhimahi, yo
            "om bhur bhuvah svah tatsavitur varenyam bhargo dhimahi yo nah prachodayat" to false, // missing devasya, dhiyo
            "om bhur bhuvah svah tatsavitur varenyam devasya dhimahi yo nah prachodayat" to false, // missing bhargo, dhiyo
            "om bhur  tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to false, // missing bhuvah, svah (2 missing -> false)
            "om bhur bhuvah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing svah (1 missing -> true)
            "om bhur svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true, // missing bhuvah (1 missing -> true)

            // 31-40: 3+ words missing (should be incorrect, overallScore < 0.85)
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi" to false, // missing last 4 words (9/13 = 0.692)
            "devasya dhimahi dhiyo yo nah prachodayat" to false, // missing first 8 words
            "om bhur bhuvah svah tatsavitur" to false,
            "om bhur bhuvah svah" to false,
            "tatsavitur varenyam bhargo" to false,
            "dhimahi dhiyo yo nah prachodayat" to false,
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya" to false,
            "om tatsavitur bhargo dhiyo prachodayat" to false,
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

    @Test
    fun testBenchmarkAudioTranscription() {
        val hindiInput = "ओम भूर भुवा स्वाहा तत्सवितुर्वरेंयं भर्गो देवस्य धीमहि धियो योना प्रचोदयात"
        val normalized = TextNormalizer.normalize(hindiInput)
        val evaluation = MantraEvaluator.evaluate(hindiInput, 14000L)
        println("=== BENCHMARK TRANSCRIPTION EVALUATION ===")
        println("Raw Hindi: $hindiInput")
        println("Normalized/Transliterated: $normalized")
        println("Overall Score: ${evaluation.overallScore}")
        println("Is Correct: ${evaluation.isCorrect}")
        println("Missing Words: ${evaluation.missingWords}")
        println("Extra Words: ${evaluation.extraWords}")
        println("Word Results:")
        evaluation.wordResults.forEach { wordRes ->
            println("  - Ref: ${wordRes.referenceWord} | Spoken: ${wordRes.spokenWord} | Score: ${wordRes.score} | Feedback: ${wordRes.feedback}")
        }
        println("==========================================")
    }

    // ---- Three-axis model (Phase 1) ----

    @Test
    fun `should_matchLegacyOverallScore_when_assessingSameInputs`() {
        // Proves no behavioral regression: assess().overall.score reproduces the
        // legacy mean-of-word-scores (== evaluate().overallScore) for every case.
        val inputs = listOf(
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat",
            "om bhur bhuvah svah tatsavitur varenyam devasya dhimahi dhiyo yo nah prachodayat", // 1 missing
            "om bhur bhuvah svah tatsavitur varenyam dhimahi dhiyo yo nah prachodayat", // 2 missing
            "om bhur bhuvah svah tatsavitur", // many missing
            "completely unrelated text spoken here",
            "" // empty
        )

        inputs.forEach { input ->
            val assessment = MantraEvaluator.assess(input, 5000L)
            val legacy = MantraEvaluator.evaluate(input, 5000L)
            val meanOfWords = legacy.wordResults.map { it.score }.average().toFloat()

            assertEquals(legacy.overallScore, assessment.overall.score, 1e-4f, "overall vs legacy for '$input'")
            assertEquals(meanOfWords, assessment.overall.score, 1e-4f, "overall vs mean for '$input'")
            assertEquals(legacy.isCorrect, assessment.isPass, "isPass vs legacy isCorrect for '$input'")
            // Phase 1: pronunciation axis is defined as the mean word similarity.
            assertEquals(assessment.pronunciation.score, assessment.overall.score, 1e-4f)
        }
    }

    @Test
    fun `should_populateAxesIndependently_when_allWordsPresentButMispronounced`() {
        // Every reference word is recited (high completeness) but each is mildly
        // mispronounced via a single-character corruption (lower pronunciation).
        val input = "on bhor bhuvar svar tatsavitar varanyam bhorgo devesya dhamahi dhayo ya noh prachodayot"
        val assessment = MantraEvaluator.assess(input, 5000L)

        // All 13 reference words aligned to a spoken word.
        assertTrue(assessment.wordResults.all { it.spokenWord != null }, "all words should align")
        assertEquals(1.0f, assessment.completeness.score, 1e-4f)
        assertEquals(ScoreBand.EXCELLENT, assessment.completeness.band)

        // Pronunciation is meaningfully below completeness and below the pass band.
        assertTrue(assessment.pronunciation.score < assessment.completeness.score)
        assertTrue(assessment.pronunciation.score < 0.85f, "pronunciation should be sub-pass")
        assertTrue(assessment.pronunciation.score > 0.0f)
    }

    @Test
    fun `should_populatePadaResults_forAllFourLines`() {
        val input = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val assessment = MantraEvaluator.assess(input, 5000L)

        assertEquals(4, assessment.padaResults.size)
        assertEquals(listOf(1, 2, 3, 4), assessment.padaResults.map { it.pada })
        assessment.padaResults.forEach { pada ->
            assertEquals(1.0f, pada.coverage, 1e-4f)
            assertEquals(1.0f, pada.averageScore, 1e-4f)
        }
    }

    @Test
    fun `should_dropPadaCoverage_when_wholeLineMissing`() {
        // Drop the entire third pada (bhargo devasya dhimahi).
        val input = "om bhur bhuvah svah tatsavitur varenyam dhiyo yo nah prachodayat"
        val assessment = MantraEvaluator.assess(input, 5000L)

        val pada3 = assessment.padaResults.first { it.pada == 3 }
        assertEquals(0.0f, pada3.coverage, 1e-4f)
        assertTrue(assessment.completeness.score < 1.0f)
    }
}
