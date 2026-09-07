/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.model.AxisScore
import com.mantrasiddhi.domain.evaluation.model.ScoreBand
import com.mantrasiddhi.domain.model.LearnerLevel

/**
 * All tunable thresholds and axis weights for the evaluation engine in one place.
 *
 * Centralizing them here makes the engine configurable (notably wiring the user's
 * `recitationSensitivity` preference into [passThreshold]) and removes magic
 * numbers. Phase 2 adds the **explicit pass rule**: the old "1 missing word passes,
 * 2 missing fails" behaviour was an accident of dividing by 13. The pass rule now
 * gates on completeness AND correctness AND the (sensitivity-driven) overall score
 * — see [isPass] — instead of an arbitrary mean threshold.
 *
 * **Calibration (Phase 5.2).** These defaults are not guesses: they were chosen by
 * sweeping them against the labeled golden corpus (`/eval/corpus.json`) and keeping
 * the values that maximise per-axis agreement with human judgment (see
 * `ThresholdTuningTest`). On the corpus the shipped defaults score:
 * correctness rank-correlation **0.934**, completeness **0.997**, pronunciation
 * (text path) **0.949**, pass/fail agreement **100%** — all above the locked
 * Phase-5.2 targets (≥0.85 correlation, ≥80% agreement) enforced by
 * `CalibrationHarnessTest`. Threshold-specific justifications are noted per field.
 *
 * @property passThreshold Overall score at/above which a recitation passes the
 *   sensitivity gate. Wired from the user's recitation-sensitivity preference; also
 *   the top ([ScoreBand.EXCELLENT]) band cutoff so the slider visibly shifts bands.
 * @property goodBand Lower cutoff for the [ScoreBand.GOOD] band.
 * @property needsWorkBand Lower cutoff for the [ScoreBand.NEEDS_WORK] band (below = [ScoreBand.POOR]).
 * @property completenessPassThreshold Minimum completeness axis required to pass
 *   (≈ "no more than one scattered word may be missing").
 * @property correctnessPassThreshold Minimum correctness axis required to pass
 *   (blocks swapped/out-of-order recitations even when every word is present).
 * @property feedbackThreshold Per-word score below which feedback is generated.
 * @property swapThreshold Similarity at which a word is judged to match a *different*
 *   reference slot (i.e. an out-of-order/swapped word).
 * @property partialThreshold Per-word score at/above which a mismatch is "partial"
 *   rather than a full pronunciation miss.
 * @property matchThreshold Minimum per-word similarity for a word to count toward
 *   correctness (not just having been aligned).
 * @property minConfidence STT confidence below which audio is treated as "unclear"
 *   and routed to a retry message instead of a false low pronunciation score.
 * @property correctnessWeight Weight of the correctness axis in [fuseOverall].
 * @property completenessWeight Weight of the completeness axis in [fuseOverall].
 * @property pronunciationWeight Weight of the pronunciation axis in [fuseOverall].
 * @property wrongWordPenalty Correctness penalty for an aligned but wrong/weak word.
 * @property swappedWordPenalty Correctness penalty for an out-of-order (swapped) word.
 * @property extraWordPenalty Correctness penalty for an inserted extra word.
 * @property inversionPenalty Correctness penalty per order inversion in the alignment.
 * @property wholePadaPenalty Extra completeness penalty for each fully-missing pada.
 * @property acousticPronunciationWeight Share of the pronunciation axis taken from the
 *   **acoustic** (DTW-vs-benchmark) signal when audio is assessed; the remainder comes
 *   from the text-phonetic estimate. Acoustic is the primary signal (§3.4), text is the
 *   graceful-degradation fallback when audio is missing or noisy.
 * @property acousticDistanceScale DTW-distance scaling used to map a per-word distance
 *   `d` to an acoustic score `exp(-d / scale)`; larger is more forgiving.
 * @property acousticEnergyThresholdRatio Fraction of the dynamic energy range above the
 *   noise floor that counts as voiced during word segmentation.
 * @property acousticMinVoicedFrames Minimum voiced frames required before acoustic
 *   scoring is attempted (too-short captures are not assessed).
 * @property minSnrDb Minimum estimated SNR (dB) for audio to be assessed acoustically;
 *   below it the capture is flagged "noisy" and pronunciation falls back to text.
 * @property swaraDistanceScale Semitone DTW-distance scale for the optional swara
 *   (pitch-accent) layer; larger is more forgiving. Maps distance `d` to `exp(-d/scale)`.
 * @property swaraMinVoicedFrames Minimum voiced frames required in both the learner
 *   and benchmark contour before a word's swara is scored.
 * @property swaraFlatThresholdSemitones Pitch-movement (std-dev, semitones) below
 *   which a learner is judged too flat while the benchmark moves clearly more.
 */
data class EvaluationConfig(
    // passThreshold sweep over {0.75..0.95} all hold 100% pass agreement on the
    // corpus (the pass/fail clusters are well separated); 0.85 keeps the legacy
    // "85% match" UX while sitting safely inside that agreement plateau.
    val passThreshold: Float = 0.85f,
    val goodBand: Float = 0.70f,
    val needsWorkBand: Float = 0.40f,
    val completenessPassThreshold: Float = 0.90f,
    val correctnessPassThreshold: Float = 0.85f,
    val feedbackThreshold: Float = 0.70f,
    val swapThreshold: Float = 0.85f,
    val partialThreshold: Float = 0.40f,
    // matchThreshold 0.50 cleanly separates real substitutions (gibberish/xyz words,
    // similarity < 0.5) from forgivable phonetic slips; lowering it to 0 collapses the
    // correctness gate (see CalibrationHarnessTest regression guard).
    val matchThreshold: Float = 0.50f,
    val minConfidence: Float = 0.10f,
    val correctnessWeight: Float = 0.34f,
    val completenessWeight: Float = 0.33f,
    val pronunciationWeight: Float = 0.33f,
    // Penalty grid sweep (ThresholdTuningTest): wrong=1.0, extra=0.5 maximises
    // correctness rank-correlation (0.934) AND pass agreement (100%); raising either
    // only lowers agreement. swapped=1.5 keeps out-of-order recitations below the
    // correctness pass gate.
    val wrongWordPenalty: Float = 1.0f,
    val swappedWordPenalty: Float = 1.5f,
    val extraWordPenalty: Float = 0.5f,
    val inversionPenalty: Float = 0.5f,
    val wholePadaPenalty: Float = 0.05f,
    val acousticPronunciationWeight: Float = 0.70f,
    val acousticDistanceScale: Float = 8.0f,
    val acousticEnergyThresholdRatio: Float = 0.15f,
    val acousticMinVoicedFrames: Int = 10,
    val minSnrDb: Float = 6.0f,
    // Swara (optional Phase 5.5): scale 4 semitones is forgiving enough for learner
    // pitch jitter while still separating a flat recitation; 5 voiced frames (~50 ms)
    // is the minimum to estimate a contour shape.
    val swaraDistanceScale: Float = 4.0f,
    val swaraMinVoicedFrames: Int = 5,
    val swaraFlatThresholdSemitones: Float = 1.0f
) {
    /** Maps a 0..1 [score] to a qualitative [ScoreBand] using the configured cutoffs. */
    fun bandFor(score: Float): ScoreBand = when {
        score >= passThreshold -> ScoreBand.EXCELLENT
        score >= goodBand -> ScoreBand.GOOD
        score >= needsWorkBand -> ScoreBand.NEEDS_WORK
        else -> ScoreBand.POOR
    }

    /** Convenience: wraps a [score] together with its [ScoreBand]. */
    fun axis(score: Float): AxisScore = AxisScore(score, bandFor(score))

    /**
     * Weighted fusion of the three axes into a single 0..1 headline score.
     *
     * This is the documented, explainable replacement for the old
     * mean-of-13-word-scores. It is the value graded against [passThreshold].
     */
    fun fuseOverall(correctness: Float, completeness: Float, pronunciation: Float): Float {
        val totalWeight = correctnessWeight + completenessWeight + pronunciationWeight
        if (totalWeight <= 0f) return 0f
        val weighted = correctness * correctnessWeight +
            completeness * completenessWeight +
            pronunciation * pronunciationWeight
        return (weighted / totalWeight).coerceIn(0f, 1f)
    }

    /**
     * The explicit, documented pass rule (replaces the accidental 0.85-of-13 gate):
     * a recitation passes only when it is sufficiently **complete** AND
     * **correct** (right words, right order) AND its **overall** score clears the
     * sensitivity-driven [passThreshold].
     */
    fun isPass(correctness: Float, completeness: Float, overall: Float): Boolean =
        completeness >= completenessPassThreshold &&
            correctness >= correctnessPassThreshold &&
            overall >= passThreshold

    /**
     * Returns a copy whose pass gate is shifted to the learner's adaptive [level]
     * (§4.3): lenient for [LearnerLevel.BEGINNER] so early progress is achievable,
     * stricter for [LearnerLevel.ADVANCED] so improvement stays visible.
     *
     * Only the *gates* move (overall/correctness/completeness pass thresholds); the
     * qualitative [goodBand]/[needsWorkBand] cutoffs are preserved so the score-to-band
     * mapping stays comparable across levels.
     *
     * @param level The learner's current adaptive difficulty tier.
     */
    fun adaptedForLevel(level: LearnerLevel): EvaluationConfig = when (level) {
        LearnerLevel.BEGINNER -> copy(
            passThreshold = BEGINNER_PASS,
            completenessPassThreshold = BEGINNER_COMPLETENESS,
            correctnessPassThreshold = BEGINNER_CORRECTNESS
        )
        LearnerLevel.INTERMEDIATE -> this
        LearnerLevel.ADVANCED -> copy(
            passThreshold = ADVANCED_PASS,
            completenessPassThreshold = ADVANCED_COMPLETENESS,
            correctnessPassThreshold = ADVANCED_CORRECTNESS
        )
    }

    companion object {
        /** Default configuration. */
        val DEFAULT = EvaluationConfig()

        // Beginner: forgiving gate to build confidence.
        private const val BEGINNER_PASS = 0.72f
        private const val BEGINNER_COMPLETENESS = 0.80f
        private const val BEGINNER_CORRECTNESS = 0.72f

        // Advanced: demanding gate so mastery is earned.
        private const val ADVANCED_PASS = 0.92f
        private const val ADVANCED_COMPLETENESS = 0.95f
        private const val ADVANCED_CORRECTNESS = 0.92f
    }
}
