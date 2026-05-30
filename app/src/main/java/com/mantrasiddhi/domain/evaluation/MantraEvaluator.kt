/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.model.PadaResult
import com.mantrasiddhi.domain.evaluation.model.RecitationAssessment
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra

/**
 * Core evaluation engine for scoring Gayatri Mantra recitation accuracy.
 *
 * The engine exposes two entry points:
 * - [assess] returns the rich three-axis [RecitationAssessment] (correctness,
 *   completeness, pronunciation and a fused overall score).
 * - [evaluate] returns the legacy single-scalar [MantraEvaluation]; it is a thin
 *   wrapper over [assess] kept for backwards compatibility.
 *
 * The reference text is read exclusively from [GayatriMantra] (single source of truth).
 */
object MantraEvaluator {

    private val reference: List<String> get() = GayatriMantra.transliterations

    /**
     * Produces a three-axis assessment of the [transcription].
     *
     * @param transcription Raw text output from speech recognition.
     * @param recitationTimeMs Time taken in milliseconds for the recitation.
     * @param config Tunable thresholds/weights (notably the sensitivity-driven pass threshold).
     * @return A [RecitationAssessment] with independently-scored axes.
     */
    fun assess(
        transcription: String,
        recitationTimeMs: Long,
        config: EvaluationConfig = EvaluationConfig.DEFAULT
    ): RecitationAssessment {
        val spokenWords = splitToWords(TextNormalizer.normalize(transcription))
        val alignment = LevenshteinAligner.align(reference, spokenWords)

        val wordResults = mutableListOf<WordResult>()
        val extraWords = mutableListOf<String>()
        evaluateWords(alignment, spokenWords, wordResults, extraWords, config)

        val pronunciationScore = meanWordScore(wordResults)
        val completenessScore = completenessScore(wordResults)
        val correctnessScore = correctnessScore(wordResults, extraWords.size, config)
        // Phase 1: the headline score reproduces the legacy mean-of-word-scores
        // exactly so no user-visible behavior changes while the axes are introduced.
        val overallScore = pronunciationScore

        return RecitationAssessment(
            correctness = config.axis(correctnessScore),
            completeness = config.axis(completenessScore),
            pronunciation = config.axis(pronunciationScore),
            overall = config.axis(overallScore),
            wordResults = wordResults,
            padaResults = buildPadaResults(wordResults),
            extraWords = extraWords,
            isPass = overallScore >= config.passThreshold,
            recitationTimeMs = recitationTimeMs
        )
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
     * @return Evaluation details including word breakdown and overall score.
     */
    fun evaluate(
        transcription: String,
        recitationTimeMs: Long,
        config: EvaluationConfig = EvaluationConfig.DEFAULT
    ): MantraEvaluation {
        val assessment = assess(transcription, recitationTimeMs, config)
        return MantraEvaluation(
            overallScore = assessment.overall.score,
            wordResults = assessment.wordResults,
            isCorrect = assessment.isPass,
            missingWords = assessment.missingWords,
            extraWords = assessment.extraWords,
            recitationTimeMs = recitationTimeMs
        )
    }

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
                val score = LevenshteinAligner.wordSimilarity(reference[i], spokenWord).toFloat()
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

        // Check if the spoken word was actually a swapped word from elsewhere in the mantra
        val isSwapped = reference.indices.any { k ->
            k != refIndex &&
                LevenshteinAligner.wordSimilarity(reference[k], spokenWord) >= config.swapThreshold
        }

        return when {
            isSwapped -> WordFeedback.SWAPPED
            score >= config.partialThreshold -> WordFeedback.PARTIAL
            else -> WordFeedback.PRONUNCIATION
        }
    }

    /**
     * Mean per-word score over all reference words (missing words count as 0).
     * Equals the legacy `overallScore` formula.
     */
    private fun meanWordScore(wordResults: List<WordResult>): Float {
        if (wordResults.isEmpty()) return 0.0f
        val total = wordResults.sumOf { it.score.toDouble() }
        return (total / wordResults.size).toFloat()
    }

    /** Fraction of reference words that were aligned to a spoken word. */
    private fun completenessScore(wordResults: List<WordResult>): Float {
        if (wordResults.isEmpty()) return 0.0f
        val recited = wordResults.count { it.spokenWord != null }
        return recited.toFloat() / wordResults.size
    }

    /**
     * Sequence/order quality: penalizes missing words, swapped (out-of-order)
     * words, weakly-matched words and extra inserted words.
     */
    private fun correctnessScore(
        wordResults: List<WordResult>,
        extraWordCount: Int,
        config: EvaluationConfig
    ): Float {
        if (wordResults.isEmpty()) return 0.0f
        val size = wordResults.size
        val missing = wordResults.count { it.spokenWord == null }
        val swapped = wordResults.count { it.feedback == WordFeedback.SWAPPED }
        val weaklyMatched = wordResults.count {
            it.spokenWord != null && it.feedback != WordFeedback.SWAPPED && it.score < config.matchThreshold
        }
        val penalties = missing + swapped + weaklyMatched + extraWordCount
        return (1.0f - penalties.toFloat() / size).coerceIn(0.0f, 1.0f)
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
