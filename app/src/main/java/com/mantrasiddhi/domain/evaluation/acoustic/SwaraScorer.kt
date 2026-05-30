/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Scores **swara (pitch-accent) accuracy** by comparing the learner's pitch contour
 * against the benchmark's, per word (Phase 5.5).
 *
 * Pipeline, per word:
 * 1. Keep only voiced frames (f0 > 0).
 * 2. Convert to **semitones relative to the contour's own median** — this is the
 *    speaker-pitch normalisation that lets a child and an adult be compared on the
 *    *shape* of their accents rather than absolute Hz.
 * 3. Align the two normalised contours with [Dtw] and map the warped distance to a
 *    `0.0..1.0` similarity.
 *
 * The overall hint contrasts pitch movement (standard deviation) and direction
 * (overall rise/fall) to tell a flat recitation from a wrong-direction one.
 *
 * Pure Kotlin: consumes f0 arrays produced by the data-layer pitch extractor, so it
 * is fully unit-testable with synthetic contours.
 */
object SwaraScorer {

    private const val SEMITONES_PER_OCTAVE = 12.0
    private const val LN2 = 0.6931471805599453

    /**
     * Scores each reference word's pitch contour against the benchmark.
     *
     * @param learnerContours Per-word learner f0 contours (Hz; `0` = unvoiced), or
     *   `null` where the word could not be located. Reference word order.
     * @param benchmarkContours Per-word benchmark f0 contours (Hz; `0` = unvoiced),
     *   same order/size as [learnerContours].
     * @param distanceScale Semitone DTW-distance scale; larger is more forgiving.
     * @param minVoicedFrames Minimum voiced frames in *both* contours to score a word.
     * @param flatThresholdSemitones Below this learner pitch-movement (std-dev), while
     *   the benchmark moves clearly more, the recitation is judged [SwaraAssessment.Hint.FLAT].
     * @return A populated [SwaraAssessment]; [SwaraAssessment.notAssessed] when no word
     *   could be scored.
     */
    fun score(
        learnerContours: List<FloatArray?>,
        benchmarkContours: List<FloatArray>,
        distanceScale: Double,
        minVoicedFrames: Int,
        flatThresholdSemitones: Float
    ): SwaraAssessment {
        val wordScores = ArrayList<Float?>(benchmarkContours.size)
        val assessedScores = ArrayList<Float>()
        var learnerMovement = 0.0
        var benchmarkMovement = 0.0
        var directionAgreement = 0.0
        var directionSamples = 0

        for (i in benchmarkContours.indices) {
            val learner = learnerContours.getOrNull(i)?.let { voicedSemitones(it) }
            val benchmark = voicedSemitones(benchmarkContours[i])
            if (learner == null || learner.size < minVoicedFrames || benchmark.size < minVoicedFrames) {
                wordScores += null
                continue
            }
            val distance = Dtw.distance(toFrames(learner), toFrames(benchmark))
            val score = distanceToScore(distance, distanceScale)
            wordScores += score
            assessedScores += score

            learnerMovement += stdDev(learner)
            benchmarkMovement += stdDev(benchmark)
            directionAgreement += if (sameSign(netSlope(learner), netSlope(benchmark))) 1.0 else -1.0
            directionSamples++
        }

        if (assessedScores.isEmpty()) return SwaraAssessment.notAssessed()
        val overall = assessedScores.average().toFloat().coerceIn(0f, 1f)
        val hint = hintFor(
            overall = overall,
            learnerMovement = learnerMovement / directionSamples,
            benchmarkMovement = benchmarkMovement / directionSamples,
            directionAgreement = if (directionSamples == 0) 0.0 else directionAgreement / directionSamples,
            flatThresholdSemitones = flatThresholdSemitones
        )
        return SwaraAssessment(wordScores = wordScores, overallScore = overall, hint = hint, assessed = true)
    }

    /** Voiced (f0 > 0) frames converted to semitones relative to their own median. */
    private fun voicedSemitones(contour: FloatArray): FloatArray {
        val voiced = contour.filter { it > 0f }
        if (voiced.isEmpty()) return FloatArray(0)
        val median = median(voiced)
        if (median <= 0f) return FloatArray(0)
        return FloatArray(voiced.size) { i ->
            (SEMITONES_PER_OCTAVE * ln(voiced[i] / median) / LN2).toFloat()
        }
    }

    private fun toFrames(values: FloatArray): List<FloatArray> = values.map { floatArrayOf(it) }

    private fun distanceToScore(distance: Double, scale: Double): Float {
        if (distance >= Double.MAX_VALUE / 4) return 0f
        val safe = if (scale <= 0.0) 1.0 else scale
        return exp(-distance / safe).toFloat().coerceIn(0f, 1f)
    }

    private fun hintFor(
        overall: Float,
        learnerMovement: Double,
        benchmarkMovement: Double,
        directionAgreement: Double,
        flatThresholdSemitones: Float
    ): SwaraAssessment.Hint = when {
        overall >= GOOD_SCORE && directionAgreement >= 0 -> SwaraAssessment.Hint.GOOD
        learnerMovement < flatThresholdSemitones && benchmarkMovement >= flatThresholdSemitones ->
            SwaraAssessment.Hint.FLAT
        directionAgreement < 0 -> SwaraAssessment.Hint.WRONG_DIRECTION
        overall >= GOOD_SCORE -> SwaraAssessment.Hint.GOOD
        else -> SwaraAssessment.Hint.UNKNOWN
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    private fun stdDev(values: FloatArray): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    /** Net rise/fall across the contour (last-third mean minus first-third mean). */
    private fun netSlope(values: FloatArray): Double {
        if (values.size < 2) return 0.0
        val third = (values.size / 3).coerceAtLeast(1)
        val head = values.take(third).average()
        val tail = values.takeLast(third).average()
        return tail - head
    }

    private fun sameSign(a: Double, b: Double): Boolean {
        if (kotlin.math.abs(a) < SLOPE_EPS || kotlin.math.abs(b) < SLOPE_EPS) return true
        return (a > 0) == (b > 0)
    }

    /** Overall swara score above which the contour is considered a good match. */
    private const val GOOD_SCORE = 0.75f

    /** Slopes smaller than this (semitones) are treated as flat, not directional. */
    private const val SLOPE_EPS = 0.5
}
