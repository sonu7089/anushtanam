/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAssessment
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticWordScore
import com.mantrasiddhi.domain.evaluation.acoustic.AudioQualityGate
import com.mantrasiddhi.domain.evaluation.model.RecitationAssessment
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Robustness & graceful-degradation audit (Phase 5.4).
 *
 * Every real-world failure mode must produce a **valid partial assessment** (all
 * three axes in `0.0..1.0`, never NaN), a clear degradation flag, and never a crash
 * or a misleading low pronunciation score for audio we could not hear. This test is
 * the executable form of the §5.4 robustness matrix for the pure-domain engine; the
 * Android-side mic/permission paths are covered by the view-model tests and the
 * best-effort capture services.
 */
class RobustnessAuditTest {

    private val perfect =
        "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"

    private fun assertValid(a: RecitationAssessment) {
        listOf(a.correctness.score, a.completeness.score, a.pronunciation.score, a.overall.score)
            .forEach {
                assertFalse(it.isNaN(), "axis score must not be NaN")
                assertTrue(it in 0f..1f, "axis score $it out of range")
            }
        assertEquals(GayatriMantra.wordCount, a.wordResults.size, "one result per reference word")
    }

    @Test
    fun `should_returnValidEmptyAssessment_when_transcriptionBlank`() {
        val a = MantraEvaluator.assess("", recitationTimeMs = 0L)
        assertValid(a)
        assertFalse(a.isPass)
        assertEquals(0f, a.completeness.score, "nothing recited -> zero completeness")
    }

    @Test
    fun `should_returnLowConfidenceFlag_when_noUsableHypothesis`() {
        val a = MantraEvaluator.assessBest(emptyList(), recitationTimeMs = 0L)
        assertValid(a)
        assertTrue(a.lowConfidence, "no hypothesis -> low confidence")
        assertFalse(a.acousticAssessed)
    }

    @Test
    fun `should_notReportFalseLowPronunciation_when_confidenceLow`() {
        // A correct recitation heard with low STT confidence must be flagged for retry,
        // not punished with a low pronunciation score.
        val a = MantraEvaluator.assess(perfect, recitationTimeMs = 0L, confidence = 0.02f)
        assertValid(a)
        assertTrue(a.lowConfidence)
        assertTrue(a.pronunciation.score > 0.8f, "pronunciation should not be falsely low")
    }

    @Test
    fun `should_degradeToTextPronunciation_when_audioMissing`() {
        val a = MantraEvaluator.assess(perfect, recitationTimeMs = 0L, acoustic = null)
        assertValid(a)
        assertFalse(a.acousticAssessed, "no audio -> text-only pronunciation")
    }

    @Test
    fun `should_degradeToTextPronunciation_when_audioTooNoisy`() {
        // Audio captured but gated out as too noisy -> fall back to text, do not zero it.
        val a = MantraEvaluator.assess(
            perfect,
            recitationTimeMs = 0L,
            acoustic = AcousticAssessment.notAssessed(snrDb = 2.0f)
        )
        assertValid(a)
        assertFalse(a.acousticAssessed)
        assertTrue(a.pronunciation.score > 0.8f, "noisy-audio fallback must not be a false low score")
    }

    @Test
    fun `should_handlePartialAcoustic_when_someWordsUnassessed`() {
        // Acoustic assessed overall, but a couple of words could not be located.
        val wordScores = GayatriMantra.words.map { w ->
            AcousticWordScore(w.index, score = if (w.index % 4 == 0) null else 0.9f, pacingRatio = 1.0f)
        }
        val acoustic = AcousticAssessment(wordScores = wordScores, assessed = true, snrDb = 20f)
        val a = MantraEvaluator.assess(perfect, recitationTimeMs = 0L, acoustic = acoustic)
        assertValid(a)
        assertTrue(a.acousticAssessed)
    }

    @Test
    fun `should_notCrashAndScoreLowCompleteness_when_recitationExtremelyShort`() {
        val a = MantraEvaluator.assess("om", recitationTimeMs = 0L)
        assertValid(a)
        assertTrue(a.completeness.score < 0.2f)
    }

    @Test
    fun `should_notCrash_when_recitationExtremelyLong`() {
        val a = MantraEvaluator.assess(
            (perfect + " ").repeat(20).trim(),
            recitationTimeMs = 600_000L
        )
        assertValid(a)
        assertTrue(a.extraWords.isNotEmpty(), "repetitions register as extra words")
    }

    @Test
    fun `should_treatGibberishAsLowAcrossAxes_when_unrelatedSpeech`() {
        val a = MantraEvaluator.assess("completely unrelated text spoken here", recitationTimeMs = 0L)
        assertValid(a)
        assertFalse(a.isPass)
    }

    @Test
    fun `should_reportUnassessable_when_audioQualityGateSeesNoiseOrSilence`() {
        // Silent / empty energy envelopes and sub-minimum captures are not assessable.
        assertEquals(0f, AudioQualityGate.estimateSnrDb(FloatArray(0)))
        assertFalse(
            AudioQualityGate.isAssessable(
                energies = FloatArray(3) { 0f },
                sttConfidence = 1.0f,
                minSnrDb = 6f,
                minConfidence = 0.1f,
                minVoicedFrames = 10
            ),
            "too few frames -> not assessable"
        )
        val noisy = FloatArray(50) { 1.0f } // flat energy -> ~0 dB SNR
        assertFalse(
            AudioQualityGate.isAssessable(
                energies = noisy,
                sttConfidence = 1.0f,
                minSnrDb = 6f,
                minConfidence = 0.1f,
                minVoicedFrames = 10
            ),
            "flat/noisy energy -> not assessable"
        )
    }
}
