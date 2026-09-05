/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [AcousticPronunciationScorer]. The headline property (§3.3): a word whose
 * audio does **not** match the benchmark scores much lower than one that does, even
 * though both could be transcribed as the same text — the case text scoring cannot
 * detect.
 */
class AcousticPronunciationScorerTest {

    private val soundA = floatArrayOf(5f, 0f)
    private val soundB = floatArrayOf(0f, 5f)
    private val scale = 8.0

    private fun features(frames: List<FloatArray>) =
        AcousticFeatures(frames = frames, energies = FloatArray(frames.size) { 1f }, frameDurationMs = 10.0)

    private fun benchmark() = listOf(
        features(listOf(soundA, soundA, soundA)),
        features(listOf(soundB, soundB, soundB))
    )

    @Test
    fun `should_scoreNearOne_when_segmentMatchesBenchmark`() {
        val learner = features(listOf(soundA, soundA, soundA, soundB, soundB, soundB))
        val segments = listOf(0..2, 3..5)
        val result = AcousticPronunciationScorer.score(learner, segments, benchmark(), scale, assessed = true, snrDb = 20f)

        assertEquals(2, result.wordScores.size)
        assertTrue(result.scoreFor(0)!! > 0.99f)
        assertTrue(result.scoreFor(1)!! > 0.99f)
    }

    @Test
    fun `should_scoreLow_when_wordMispronouncedEvenThoughTextWouldMatch`() {
        // Learner says sound A again where word 1 should sound like B — the dictation
        // engine might still print the right word, but the audio is wrong.
        val learner = features(listOf(soundA, soundA, soundA, soundA, soundA, soundA))
        val segments = listOf(0..2, 3..5)
        val result = AcousticPronunciationScorer.score(learner, segments, benchmark(), scale, assessed = true, snrDb = 20f)

        assertTrue(result.scoreFor(0)!! > 0.99f, "correct word stays high")
        assertTrue(result.scoreFor(1)!! < 0.7f, "mispronounced word scores meaningfully lower")
        assertTrue(result.scoreFor(1)!! < result.scoreFor(0)!!)
    }

    @Test
    fun `should_reportUnassessed_when_gateFailed`() {
        val learner = features(listOf(soundA, soundA, soundA))
        val result = AcousticPronunciationScorer.score(learner, listOf(0..2, 0..2), benchmark(), scale, assessed = false, snrDb = 1f)

        assertEquals(2, result.wordScores.size)
        assertTrue(result.wordScores.all { it.score == null })
    }

    @Test
    fun `should_leaveWordUnassessed_when_segmentNull`() {
        val learner = features(listOf(soundA, soundA, soundA))
        val result = AcousticPronunciationScorer.score(learner, listOf(0..2, null), benchmark(), scale, assessed = true, snrDb = 20f)

        assertNotNull(result.scoreFor(0))
        assertNull(result.scoreFor(1))
    }

    @Test
    fun `should_computePacingRatio_relativeToBenchmark`() {
        // Learner word 0 lasts 6 frames vs benchmark's 3 -> ~2x slower.
        val learner = features(listOf(soundA, soundA, soundA, soundA, soundA, soundA, soundB, soundB, soundB))
        val segments = listOf(0..5, 6..8)
        val result = AcousticPronunciationScorer.score(learner, segments, benchmark(), scale, assessed = true, snrDb = 20f)

        val pacing = result.wordScoreFor(0)!!.pacingRatio!!
        assertEquals(2.0f, pacing, 0.2f)
        assertEquals(AcousticWordScore.Pacing.TOO_SLOW, result.wordScoreFor(0)!!.pacing)
    }
}
