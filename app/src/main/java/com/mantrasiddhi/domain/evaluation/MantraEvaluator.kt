/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.model.PadaResult
import com.mantrasiddhi.domain.evaluation.model.RecitationAssessment
import com.mantrasiddhi.domain.evaluation.phonetics.PhoneticDistance
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import com.mantrasiddhi.domain.evaluation.scoring.CompletenessScorer
import com.mantrasiddhi.domain.evaluation.scoring.PronunciationScorer
import com.mantrasiddhi.domain.evaluation.scoring.SequenceScorer

/**
 * Core evaluation engine for scoring Gayatri Mantra recitation accuracy.
 *
 * The engine exposes two entry points:
 * - [assess] returns the rich three-axis [RecitationAssessment] (correctness,
 *   completeness, pronunciation and a fused overall score).
 * - [evaluate] returns the legacy single-scalar [MantraEvaluation]; it is a thin
 *   wrapper over [assess] kept for backwards compatibility.
 *
 * Phase 2 changes the internals: words are compared as **phoneme tokens** with
 * feature-weighted distance ([PhoneticDistance]), each axis is computed from real
 * structure by a dedicated scorer, and the pass/fail decision follows the explicit
 * rule in [EvaluationConfig] rather than an accidental mean threshold.
 *
 * The reference text is read exclusively from [GayatriMantra] (single source of truth).
 */
object MantraEvaluator {

    private val reference: List<String> get() = GayatriMantra.transliterations

    /** Phonetic similarity used for both word-sequence alignment and per-word scoring. */
    private val phoneticSimilarity: (String, String) -> Double = PhoneticDistance::wordSimilarity

    /**
     * Produces a three-axis assessment of the [transcription].
     *
     * @param transcription Raw text output from speech recognition.
     * @param recitationTimeMs Time taken in milliseconds for the recitation.
     * @param config Tunable thresholds/weights (notably the sensitivity-driven pass threshold).
     * @param confidence STT confidence of the transcription (`0.0..1.0`); below
     *   [EvaluationConfig.minConfidence] the result is flagged [RecitationAssessment.lowConfidence].
     * @return A [RecitationAssessment] with independently-scored axes.
     */
    fun assess(
        transcription: String,
        recitationTimeMs: Long,
        config: EvaluationConfig = EvaluationConfig.DEFAULT,
        confidence: Float = 1.0f
    ): RecitationAssessment {
        val spokenWords = splitToWords(TextNormalizer.normalize(transcription))
        val alignment = LevenshteinAligner.align(reference, spokenWords, phoneticSimilarity)

        val wordResults = mutableListOf<WordResult>()
        val extraWords = mutableListOf<String>()
        evaluateWords(alignment, spokenWords, wordResults, extraWords, config)
        val padaResults = buildPadaResults(wordResults)

        val completenessScore = CompletenessScorer.score(wordResults, padaResults, config.wholePadaPenalty)
        val correctnessScore = SequenceScorer.score(wordResults, alignment, extraWords.size, config)
        val pronunciationScore = PronunciationScorer.score(wordResults)
        val overallScore = config.fuseOverall(correctnessScore, completenessScore, pronunciationScore)

        return RecitationAssessment(
            correctness = config.axis(correctnessScore),
            completeness = config.axis(completenessScore),
            pronunciation = config.axis(pronunciationScore),
            overall = config.axis(overallScore),
            wordResults = wordResults,
            padaResults = padaResults,
            extraWords = extraWords,
            isPass = config.isPass(correctnessScore, completenessScore, overallScore),
            lowConfidence = confidence < config.minConfidence,
            recitationTimeMs = recitationTimeMs
        )
    }

    /**
     * Assesses the best-aligning hypothesis among STT n-best [alternatives].
     *
     * Dictation engines often place the most faithful transcription in hypothesis
     * #2; scoring all of them and keeping the best recovers that signal.
     *
     * @param alternatives Candidate transcriptions paired with their confidence,
     *   ordered best-first.
     * @param recitationTimeMs Time taken in milliseconds for the recitation.
     * @param config Tunable thresholds/weights.
     * @return The assessment of the chosen hypothesis (an empty-input assessment when
     *   no hypothesis is usable).
     */
    fun assessBest(
        alternatives: List<Pair<String, Float>>,
        recitationTimeMs: Long,
        config: EvaluationConfig = EvaluationConfig.DEFAULT
    ): RecitationAssessment {
        val chosen = HypothesisSelector.select(alternatives) { text ->
            assess(text, recitationTimeMs, config).overall.score
        } ?: return assess("", recitationTimeMs, config, confidence = 0.0f)
        return assess(chosen.first, recitationTimeMs, config, confidence = chosen.second)
    }

    /**
     * Evaluates the spoken transcription against the reference mantra.
     *
     * Compatibility shim over [assess]: [MantraEvaluation.overallScore] and
     * [MantraEvaluation.isCorrect] are derived from the three-axis assessment.
     *
     * @param transcription Raw text output from speech recognition.
     * @param recitationTimeMs Time taken in milliseconds for the recitation.
     * @param config Tunable thresholds/weights.
     * @param confidence STT confidence of the transcription (`0.0..1.0`).
     * @return Evaluation details including word breakdown and overall score.
     */
    fun evaluate(
        transcription: String,
        recitationTimeMs: Long,
        config: EvaluationConfig = EvaluationConfig.DEFAULT,
        confidence: Float = 1.0f
    ): MantraEvaluation {
        val assessment = assess(transcription, recitationTimeMs, config, confidence)
        return assessment.toEvaluation()
    }

    /**
     * Evaluates the best-aligning hypothesis among STT n-best [alternatives].
     *
     * @param alternatives Candidate transcriptions paired with their confidence.
     * @param recitationTimeMs Time taken in milliseconds for the recitation.
     * @param config Tunable thresholds/weights.
     */
    fun evaluateBest(
        alternatives: List<Pair<String, Float>>,
        recitationTimeMs: Long,
        config: EvaluationConfig = EvaluationConfig.DEFAULT
    ): MantraEvaluation = assessBest(alternatives, recitationTimeMs, config).toEvaluation()

    private fun RecitationAssessment.toEvaluation(): MantraEvaluation = MantraEvaluation(
        overallScore = overall.score,
        wordResults = wordResults,
        isCorrect = isPass,
        missingWords = missingWords,
        extraWords = extraWords,
        recitationTimeMs = recitationTimeMs,
        lowConfidence = lowConfidence
    )

    private fun splitToWords(normalized: String): List<String> =
        if (normalized.isEmpty()) emptyList() else normalized.split(" ")

    private fun evaluateWords(
        alignment: List<AlignmentStep>,
        spokenWords: List<String>,
        wordResults: MutableList<WordResult>,
        extraWords: MutableList<String>,
        config: EvaluationConfig
    ) {
        val stepMap = alignment.filter { it.refIndex != null }.associateBy { it.refIndex!! }

        for (i in reference.indices) {
            val step = stepMap[i]
            if (step != null && step.spokenIndex != null) {
                val spokenWord = spokenWords[step.spokenIndex]
                val score = phoneticSimilarity(reference[i], spokenWord).toFloat()
                val feedback = determineFeedback(spokenWord, score, i, config)
                wordResults.add(WordResult(reference[i], spokenWord, score, feedback))
            } else {
                wordResults.add(WordResult(reference[i], null, 0.0f, WordFeedback.MISSING))
            }
        }

        alignment.filter { it.refIndex == null && it.spokenIndex != null }.forEach {
            extraWords.add(spokenWords[it.spokenIndex!!])
        }
    }

    private fun determineFeedback(
        spokenWord: String,
        score: Float,
        refIndex: Int,
        config: EvaluationConfig
    ): WordFeedback? {
        if (score >= config.feedbackThreshold) return null

        // Did the spoken word actually match a *different* reference slot (a swap)?
        val isSwapped = reference.indices.any { k ->
            k != refIndex &&
                phoneticSimilarity(reference[k], spokenWord) >= config.swapThreshold
        }

        return when {
            isSwapped -> WordFeedback.SWAPPED
            score >= config.partialThreshold -> WordFeedback.PARTIAL
            else -> WordFeedback.PRONUNCIATION
        }
    }

    private fun buildPadaResults(wordResults: List<WordResult>): List<PadaResult> {
        return (1..GayatriMantra.PADA_COUNT).map { pada ->
            val indices = GayatriMantra.wordsForPada(pada).map { it.index }
            val padaWords = indices.mapNotNull { wordResults.getOrNull(it) }
            val coverage = if (padaWords.isEmpty()) {
                0.0f
            } else {
                padaWords.count { it.spokenWord != null }.toFloat() / padaWords.size
            }
            val average = if (padaWords.isEmpty()) {
                0.0f
            } else {
                padaWords.sumOf { it.score.toDouble() }.toFloat() / padaWords.size
            }
            PadaResult(pada = pada, coverage = coverage, averageScore = average, wordResults = padaWords)
        }
    }
}
