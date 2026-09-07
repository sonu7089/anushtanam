/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.mantra.MantraCatalogReader
import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.usecase.GetPracticePlanUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The extensibility claim, exercised rather than asserted: a mantra that exists **only** as
 * a JSON string in this file — no Kotlin, no bundled audio, no entry in the catalog — is
 * scored, given feedback and scheduled for practice by the same engine that scores the
 * Gayatri, and neither mantra's numbers leak into the other's.
 *
 * If adding a mantra ever comes to need more than a definition file, this test is where it
 * will show up first.
 */
class SecondMantraTest {

    /** Mahamrityunjaya, as a definition file would spell it. No benchmark recording. */
    private val mahamrityunjaya = MantraCatalogReader.readMantra(
        """
        {
          "id": "mahamrityunjaya",
          "name": "Mahamrityunjaya Mantra",
          "padaCount": 2,
          "aksharaCount": 32,
          "textRules": {
            "compoundReplacements": [
              { "from": "tryambakam yajamahe", "to": "tryambakam yajamahe" }
            ],
            "spellingOverrides": { "trayambakam": "tryambakam" }
          },
          "words": [
            { "devanagari": "ॐ", "transliteration": "om", "syllables": ["om"], "pada": 1 },
            {
              "devanagari": "त्र्यम्बकं", "transliteration": "tryambakam",
              "syllables": ["tryam", "ba", "kam"], "pada": 1
            },
            {
              "devanagari": "यजामहे", "transliteration": "yajamahe",
              "syllables": ["ya", "ja", "ma", "he"], "pada": 1
            },
            {
              "devanagari": "सुगन्धिं", "transliteration": "sugandhim",
              "syllables": ["su", "gan", "dhim"], "pada": 2
            },
            {
              "devanagari": "पुष्टिवर्धनम्", "transliteration": "pushtivardhanam",
              "syllables": ["push", "ti", "var", "dha", "nam"], "pada": 2
            }
          ]
        }
        """.trimIndent(),
        source = "SecondMantraTest"
    )

    @Test
    fun `a mantra defined only in JSON is scored perfectly when recited correctly`() {
        val assessment = MantraEvaluator.assess(
            mahamrityunjaya,
            "om tryambakam yajamahe sugandhim pushtivardhanam",
            recitationTimeMs = 6_000L
        )

        assertEquals(1.0f, assessment.overall.score, 0.001f)
        assertTrue(assessment.isPass)
        assertTrue(assessment.missingWords.isEmpty())
        assertEquals(5, assessment.wordResults.size)
        assertEquals(2, assessment.padaResults.size)
    }

    @Test
    fun `its own spelling overrides are applied, and the Gayatri's are not`() {
        val assessment = MantraEvaluator.assess(
            mahamrityunjaya,
            "om trayambakam yajamahe sugandhim pushtivardhanam",
            recitationTimeMs = 6_000L
        )
        assertEquals(1.0f, assessment.wordResults[1].score, 0.001f)

        // "bharago" is a Gayatri quirk; it must not be canonicalised for another mantra.
        assertEquals(
            "bharago",
            TextNormalizer.normalize("bharago", mahamrityunjaya.textRules)
        )
        assertEquals("bhargo", TextNormalizer.normalize("bharago", gayatri.textRules))
    }

    @Test
    fun `missing words and padas are reported against its own structure`() {
        val assessment = MantraEvaluator.assess(
            mahamrityunjaya,
            "om tryambakam yajamahe",
            recitationTimeMs = 3_000L
        )

        assertEquals(listOf("sugandhim", "pushtivardhanam"), assessment.missingWords)
        assertEquals(1.0f, assessment.padaResults[0].coverage, 0.001f)
        assertEquals(0.0f, assessment.padaResults[1].coverage, 0.001f)
        assertFalse(assessment.isPass)
    }

    @Test
    fun `feedback names a syllable from its own breakdown`() {
        val tip = ImprovementSuggestionEngine.tipForWordResult(
            mahamrityunjaya,
            WordResult(
                referenceWord = "pushtivardhanam",
                spokenWord = "pustivardhanam",
                score = 0.7f,
                feedback = WordFeedback.PRONUNCIATION
            )
        )
        assertTrue(tip != null, "a mispronounced word should produce a tip")
        assertTrue(
            mahamrityunjaya.wordFor("pushtivardhanam")!!.syllables.contains(tip!!.syllable),
            "tip named '${tip.syllable}', which is not one of the word's syllables"
        )
    }

    @Test
    fun `a mantra with no benchmark simply has no audio windows`() {
        assertFalse(mahamrityunjaya.hasBenchmarkTimings)
        assertEquals(List(5) { 0.0 }, mahamrityunjaya.benchmarkWordDurationsMs)
    }

    @Test
    fun `the practice plan schedules only the words of the mantra it was given`() {
        val session = RecitationSession(
            userId = 1L,
            mantraId = mahamrityunjaya.id,
            mode = RecitationMode.NORMAL,
            overallScore = 0.6,
            wordScores = mapOf(
                "om" to 1.0,
                "tryambakam" to 1.0,
                "yajamahe" to 1.0,
                "sugandhim" to 1.0,
                "pushtivardhanam" to 0.2
            ),
            transcription = "",
            isCorrect = false,
            durationMs = 6_000L,
            createdAt = 1_700_000_000_000L
        )

        val plan = GetPracticePlanUseCase.plan(
            mahamrityunjaya,
            listOf(session),
            now = 1_700_000_000_000L,
            limit = 3
        )

        assertEquals("pushtivardhanam", plan.items.first().word)
        assertTrue(
            plan.items.all { it.word in mahamrityunjaya.transliterations },
            "plan scheduled a word from another mantra: ${plan.items.map { it.word }}"
        )
    }
}
