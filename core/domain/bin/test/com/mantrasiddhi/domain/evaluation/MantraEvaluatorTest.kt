/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
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
        val evaluation = MantraEvaluator.evaluate(gayatri, input, 5000L)

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
        val evaluation = MantraEvaluator.evaluate(gayatri, input, 5000L)

        assertEquals(1.0f, evaluation.overallScore, 0.001f)
        assertTrue(evaluation.isCorrect)
        assertTrue(evaluation.missingWords.isEmpty())
    }

    @Test
    fun `should_detectMissingWords_when_wordsSkipped`() {
        // Skipping "bhuvah" and "devasya"
        val input = "om bhur svah tatsavitur varenyam bhargo dhimahi dhiyo yo nah prachodayat"
        val evaluation = MantraEvaluator.evaluate(gayatri, input, 4000L)

        assertTrue(evaluation.missingWords.contains("bhuvah"))
        assertTrue(evaluation.missingWords.contains("devasya"))
        assertEquals(2, evaluation.missingWords.size)

        val bhuvahResult = evaluation.wordResults.first { it.referenceWord == "bhuvah" }
        assertEquals(0.0f, bhuvahResult.score)
        assertEquals(WordFeedback.MISSING, bhuvahResult.feedback)
    }

    @Test
    fun `should_detectExtraWords_when_unrelatedWordsAdded`() {
        val input = "om bhur bhuvah svah extra tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat random"
        val evaluation = MantraEvaluator.evaluate(gayatri, input, 6000L)

        assertEquals(2, evaluation.extraWords.size)
        assertTrue(evaluation.extraWords.contains("extra"))
        assertTrue(evaluation.extraWords.contains("random"))
        assertTrue(evaluation.isCorrect) // A couple of stray words don't fail an otherwise-perfect recitation
    }

    @Test
    fun `should_flagSwappedWords_when_wordOrderIncorrect`() {
        // Swapping "bhur" and "bhuvah"
        val input = "om bhuvah bhur svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val evaluation = MantraEvaluator.evaluate(gayatri, input, 5000L)

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
        val evaluation = MantraEvaluator.evaluate(gayatri, input, 5000L)

        val bhargoResult = evaluation.wordResults.first { it.referenceWord == "bhargo" }
        assertEquals(WordFeedback.PRONUNCIATION, bhargoResult.feedback)
        assertTrue(bhargoResult.score < 0.4f)
    }

    @Test
    fun `should_generatePartialFeedback_when_wordClose`() {
        // "tatsavitur" replaced with "tatsav" (close match)
        val input = "om bhur bhuvah svah tatsav varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val evaluation = MantraEvaluator.evaluate(gayatri, input, 5000L)

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
            val evaluation = MantraEvaluator.evaluate(gayatri, input, 5000L)
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
        val evaluation = MantraEvaluator.evaluate(gayatri, hindiInput, 14000L)
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

    // ---- Three-axis model (Phase 2: explicit pass rule) ----

    @Test
    fun `should_passOnlyWhenCompleteCorrectAndOverallClearGate_when_assessing`() {
        // The explicit pass rule (EvaluationConfig.isPass) gates on completeness AND
        // correctness AND the sensitivity-driven overall score — no longer a mean.
        val cases = listOf(
            // perfect -> pass
            "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat" to true,
            // one scattered word missing (12/13 complete) -> pass
            "om bhur bhuvah svah tatsavitur varenyam devasya dhimahi dhiyo yo nah prachodayat" to true,
            // two words missing (11/13) -> fails the completeness gate
            "om bhur bhuvah svah tatsavitur varenyam dhimahi dhiyo yo nah prachodayat" to false,
            // a whole pada dropped -> fails completeness
            "om bhur bhuvah svah tatsavitur varenyam dhiyo yo nah prachodayat" to false,
            // empty -> fail
            "" to false
        )
        cases.forEach { (input, expected) ->
            val assessment = MantraEvaluator.assess(gayatri, input, 5000L)
            assertEquals(expected, assessment.isPass, "pass rule for '$input'")
        }
    }

    @Test
    fun `should_blockPass_when_wordsSwappedEvenThoughComplete`() {
        // Every word present (high completeness) but two are transposed: the explicit
        // correctness gate must block the pass. This is impossible to express with a
        // single mean score.
        val input = "om bhuvah bhur svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val assessment = MantraEvaluator.assess(gayatri, input, 5000L)

        assertEquals(1.0f, assessment.completeness.score, 1e-4f)
        assertTrue(assessment.correctness.score < 0.85f, "swaps must lower correctness")
        assertFalse(assessment.isPass, "a swapped recitation must not pass")
    }

    @Test
    fun `should_separatePronunciationFromCompleteness_when_allWordsMispronounced`() {
        // Every reference word is recited (high completeness) but each is mildly
        // mispronounced (lower pronunciation) — the two axes must diverge.
        val input = "on bhor bhuvar svar tatsavitar varanyam bhorgo devesya dhamahi dhayo ya noh prachodayot"
        val assessment = MantraEvaluator.assess(gayatri, input, 5000L)

        assertTrue(assessment.wordResults.all { it.spokenWord != null }, "all words should align")
        assertEquals(1.0f, assessment.completeness.score, 1e-4f)
        assertEquals(ScoreBand.EXCELLENT, assessment.completeness.band)
        assertTrue(assessment.pronunciation.score < assessment.completeness.score)
        assertTrue(assessment.pronunciation.score > 0.0f)
    }

    @Test
    fun `should_movePassBoundary_when_passThresholdChanged`() {
        // Sensitivity (passThreshold) measurably shifts the overall pass gate.
        val input = "om bhur bhuvah svah tatsavitur varenyam devasya dhimahi dhiyo yo nah prachodayat"
        val overall = MantraEvaluator.assess(gayatri, input, 5000L).overall.score

        val lenient = EvaluationConfig.DEFAULT.copy(passThreshold = overall - 0.05f)
        val strict = EvaluationConfig.DEFAULT.copy(passThreshold = overall + 0.05f)
        assertTrue(MantraEvaluator.assess(gayatri, input, 5000L, lenient).isPass, "should pass below the gate")
        assertFalse(MantraEvaluator.assess(gayatri, input, 5000L, strict).isPass, "should fail above the gate")
    }

    @Test
    fun `should_recoverCorrectWords_when_inLowerRankedHypothesis`() {
        // The 1-best hypothesis has a wrong word; the correct full mantra sits in #2.
        val wrong = "om bhur bhuvah svah tatsavitur varenyam xyz devasya dhimahi dhiyo yo nah prachodayat"
        val correct = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val alternatives = listOf(wrong to 0.9f, correct to 0.6f)

        val assessment = MantraEvaluator.assessBest(gayatri, alternatives, 5000L)
        assertEquals(correct.split(" ").size, assessment.wordResults.size)
        assertTrue(assessment.isPass, "n-best selection should recover the correct recitation")
        assertTrue(assessment.overall.score > MantraEvaluator.assess(gayatri, wrong, 5000L).overall.score)
    }

    @Test
    fun `should_flagLowConfidence_when_sttConfidenceBelowThreshold`() {
        val input = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val assessment = MantraEvaluator.assess(gayatri, input, 5000L, confidence = 0.02f)
        assertTrue(assessment.lowConfidence, "very low confidence should be flagged")
    }

    @Test
    fun `should_populatePadaResults_forAllFourLines`() {
        val input = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val assessment = MantraEvaluator.assess(gayatri, input, 5000L)

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
        val assessment = MantraEvaluator.assess(gayatri, input, 5000L)

        val pada3 = assessment.padaResults.first { it.pada == 3 }
        assertEquals(0.0f, pada3.coverage, 1e-4f)
        assertTrue(assessment.completeness.score < 1.0f)
    }
}
