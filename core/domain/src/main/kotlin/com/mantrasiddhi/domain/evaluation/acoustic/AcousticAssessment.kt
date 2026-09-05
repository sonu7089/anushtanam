/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

/**
 * The result of the acoustic pronunciation layer: a per-word acoustic similarity
 * to the benchmark recording, plus a quality verdict.
 *
 * The text engine fuses [wordScores] into the pronunciation axis when [assessed]
 * is true; when it is false (no audio, or audio gated out as too noisy) the engine
 * degrades cleanly to the Phase-2 text-only pronunciation estimate and surfaces a
 * "not assessed — noisy audio" hint instead of a false low score.
 *
 * @property wordScores One [AcousticWordScore] per reference word, in word-index
 *   order. Individual entries may still be unassessed even when [assessed] is true
 *   (e.g. a single dropped word).
 * @property assessed Whether the audio was good enough to trust acoustically at all.
 * @property snrDb Estimated signal-to-noise ratio of the capture, in decibels
 *   (diagnostic; drives [assessed] together with STT confidence).
 */
data class AcousticAssessment(
    val wordScores: List<AcousticWordScore>,
    val assessed: Boolean,
    val snrDb: Float
) {
    /** Returns the acoustic score for [wordIndex], or `null` if absent/unassessed. */
    fun scoreFor(wordIndex: Int): Float? =
        wordScores.firstOrNull { it.wordIndex == wordIndex }?.score

    /** Returns the [AcousticWordScore] for [wordIndex], or `null` if absent. */
    fun wordScoreFor(wordIndex: Int): AcousticWordScore? =
        wordScores.firstOrNull { it.wordIndex == wordIndex }

    companion object {
        /** A "could not assess" result (no audio / too noisy). */
        fun notAssessed(snrDb: Float = 0f): AcousticAssessment =
            AcousticAssessment(emptyList(), assessed = false, snrDb = snrDb)
    }
}
