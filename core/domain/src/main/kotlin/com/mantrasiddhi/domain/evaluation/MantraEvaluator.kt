/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAssessment
import com.mantrasiddhi.domain.evaluation.model.PadaResult
import com.mantrasiddhi.domain.evaluation.model.RecitationAssessment
import com.mantrasiddhi.domain.evaluation.phonetics.PhoneticDistance
import com.mantrasiddhi.domain.evaluation.scoring.CompletenessScorer
import com.mantrasiddhi.domain.evaluation.scoring.PronunciationScorer
import com.mantrasiddhi.domain.evaluation.scoring.SequenceScorer
import com.mantrasiddhi.domain.mantra.Mantra

/**
 * Core evaluation engine: scores a recitation against the mantra it was recited from.
 *
 * The engine exposes two entry points:
 * - [assess] returns the rich three-axis [RecitationAssessment] (correctness,
 *   completeness, pronunciation and a fused overall score).
 * - [evaluate] returns the legacy single-scalar [MantraEvaluation]; it is a thin wrapper
 *   over [assess] kept for backwards compatibility.
 *
 * Words are compared as **phoneme tokens** with feature-weighted distance
 * ([PhoneticDistance]), each axis is computed from real structure by a dedicated scorer,
 * and the pass/fail decision follows the explicit rule in [EvaluationConfig] rather than
 * an accidental mean threshold.
 *
 * The engine is stateless and holds no reference text of its own: every entry point takes
 * the [Mantra] to score against, which is what lets the app carry more than one.
 */
object MantraEvaluator {

    /** Phonetic similarity used for both word-sequence alignment and per-word scoring. */
    private val phoneticSimilarity: (String, String) -> Double = PhoneticDistance::wordSimilarity

    /**
     * Produces a three-axis assessment of the [transcription].
     *
     * @param mantra The mantra being recited; supplies the reference words, the pada
     *   layout and the dictation quirks used to normalize [transcription].
     * @param transcription Raw text output from speech recognition.
     * @param recitationTimeMs Time taken in milliseconds for the recitation.
     * @param config Tunable thresholds/weights (notably the sensitivity-driven pass threshold).
     * @param confidence STT confidence of the transcription (`0.0..1.0`); below
     *   [EvaluationConfig.minConfidence] the result is flagged [RecitationAssessment.lowConfidence].
     * @param acoustic Optional acoustic assessment. When present and
     *   [AcousticAssessment.assessed], the pronunciation axis is fused from audio
     *   (primary) and text (fallback); otherwise the engine uses the text-only estimate
     *   so it degrades cleanly when audio is unavailable.
     * @return A [RecitationAssessment] with independently-scored axes.
     */
    fun assess(
        mantra: Mantra,
        transcription: String,
        recitationTimeMs: Long,
        config: EvaluationConfig = EvaluationConfig.DEFAULT,
        confidence: Float = 1.0f,
        acoustic: AcousticAssessment? = null
    ): RecitationAssessment {
        val reference = mantra.transliterations
        val spokenWords = splitToWords(TextNormalizer.normalize(transcription, mantra.textRules))
        val alignment = LevenshteinAligner.align(reference, spokenWords, phoneticSimilarity)

        val wordResults = buildWordResults(reference, alignment, spokenWords, config, acoustic)
        val extraWords = extraWords(alignment, spokenWords)
        val padaResults = buildPadaResults(mantra, wordResults)

        val completenessScore = CompletenessScorer.score(wordResults, padaResults, config.wholePadaPenalty)
        val correctnessScore = SequenceScorer.score(wordResults, alignment, extraWords.size, config)
        val pronunciationScore = PronunciationScorer.score(mantra, wordResults, acoustic, config)
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
            acousticAssessed = acoustic != null && acoustic.assessed,
            recitationTimeMs = recitationTimeMs
        )
    }

    /**
     * Assesses the best-aligning hypothesis among STT n-best [alternatives].
     *
     * Dictation engines often place the most faithful transcription in hypothesis #2;
     * scoring all of them and keeping the best recovers that signal.
     *
     * @param mantra The mantra being recited.
     * @param alternatives Candidate transcriptions paired with their confidence,
     *   ordered best-first.
     * @param recitationTimeMs Time taken in milliseconds for the recitation.
     * @param config Tunable thresholds/weights.
     * @param acoustic Optional acoustic assessment fused into pronunciation.
     * @return The assessment of the chosen hypothesis (an empty-input assessment when
     *   no hypothesis is usable).
     */
    fun assessBest(
        mantra: Mantra,
        alternatives: List<Pair<String, Float>>,
        recitationTimeMs: Long,
        config: EvaluationConfig = EvaluationConfig.DEFAULT,
        acoustic: AcousticAssessment? = null
    ): RecitationAssessment {
        // The acoustic signal is independent of which text hypothesis we pick, so
        // hypothesis selection still uses the text-only overall score.
        val chosen = HypothesisSelector.select(alternatives) { text ->
            assess(mantra, text, recitationTimeMs, config).overall.score
        } ?: return assess(mantra, "", recitationTimeMs, config, confidence = 0.0f, acoustic = acoustic)
        return assess(mantra, chosen.first, recitationTimeMs, config, chosen.second, acoustic)
    }

    /**
     * Evaluates the spoken transcription against the reference mantra.
     *
     * Compatibility shim over [assess]: [MantraEvaluation.overallScore] and
     * [MantraEvaluation.isCorrect] are derived from the three-axis assessment.
     *
     * @param mantra The mantra being recited.
     * @param transcription Raw text output from speech recognition.
     * @param recitationTimeMs Time taken in milliseconds for the recitation.
     * @param config Tunable thresholds/weights.
     * @param confidence STT confidence of the transcription (`0.0..1.0`).
     * @param acoustic Optional acoustic assessment fused into pronunciation.
     * @return Evaluation details including word breakdown and overall score.
     */
    fun evaluate(
        mantra: Mantra,
        transcription: String,
        recitationTimeMs: Long,
        config: EvaluationConfig = EvaluationConfig.DEFAULT,
        confidence: Float = 1.0f,
        acoustic: AcousticAssessment? = null
    ): MantraEvaluation =
        toEvaluation(assess(mantra, transcription, recitationTimeMs, config, confidence, acoustic))

    /**
     * Evaluates the best-aligning hypothesis among STT n-best [alternatives].
     *
     * @param mantra The mantra being recited.
     * @param alternatives Candidate transcriptions paired with their confidence.
     * @param recitationTimeMs Time taken in milliseconds for the recitation.
     * @param config Tunable thresholds/weights.
     * @param acoustic Optional acoustic assessment fused into pronunciation.
     */
    fun evaluateBest(
        mantra: Mantra,
        alternatives: List<Pair<String, Float>>,
        recitationTimeMs: Long,
        config: EvaluationConfig = EvaluationConfig.DEFAULT,
        acoustic: AcousticAssessment? = null
    ): MantraEvaluation = toEvaluation(assessBest(mantra, alternatives, recitationTimeMs, config, acoustic))

    /**
     * Converts a rich [RecitationAssessment] into the legacy single-scalar
     * [MantraEvaluation]. Exposed so callers that need *both* the three-axis breakdown
     * (e.g. to persist per-axis scores) and the legacy result can derive them from a
     * single assessment without re-evaluating.
     */
    fun toEvaluation(assessment: RecitationAssessment): MantraEvaluation = MantraEvaluation(
        overallScore = assessment.overall.score,
        wordResults = assessment.wordResults,
        isCorrect = assessment.isPass,
        missingWords = assessment.missingWords,
        extraWords = assessment.extraWords,
        recitationTimeMs = assessment.recitationTimeMs,
        lowConfidence = assessment.lowConfidence,
        acousticAssessed = assessment.acousticAssessed
    )

    private fun splitToWords(normalized: String): List<String> =
        if (normalized.isEmpty()) emptyList() else normalized.split(" ")

    /** One result per reference word, in mantra order; unaligned words come back MISSING. */
    private fun buildWordResults(
        reference: List<String>,
        alignment: List<AlignmentStep>,
        spokenWords: List<String>,
        config: EvaluationConfig,
        acoustic: AcousticAssessment?
    ): List<WordResult> {
        val stepByReferenceIndex = alignment.filter { it.refIndex != null }.associateBy { it.refIndex }
        val useAcoustic = acoustic != null && acoustic.assessed

        return reference.indices.map { index ->
            val spokenIndex = stepByReferenceIndex[index]?.spokenIndex
                ?: return@map WordResult(reference[index], null, 0.0f, WordFeedback.MISSING)

            val spokenWord = spokenWords[spokenIndex]
            val score = phoneticSimilarity(reference[index], spokenWord).toFloat()
            val acousticWord = if (useAcoustic) acoustic?.wordScoreFor(index) else null
            WordResult(
                referenceWord = reference[index],
                spokenWord = spokenWord,
                score = score,
                feedback = determineFeedback(reference, spokenWord, score, index, config),
                acousticScore = acousticWord?.score,
                pacingRatio = acousticWord?.pacingRatio
            )
        }
    }

    /** Spoken words that aligned to no reference word at all. */
    private fun extraWords(alignment: List<AlignmentStep>, spokenWords: List<String>): List<String> =
        alignment.filter { it.refIndex == null && it.spokenIndex != null }
            .map { spokenWords[it.spokenIndex!!] }

    private fun determineFeedback(
        reference: List<String>,
        spokenWord: String,
        score: Float,
        refIndex: Int,
        config: EvaluationConfig
    ): WordFeedback? {
        if (score >= config.feedbackThreshold) return null

        // Did the spoken word actually match a *different* reference slot (a swap)?
        val isSwapped = reference.indices.any { k ->
            k != refIndex && phoneticSimilarity(reference[k], spokenWord) >= config.swapThreshold
        }

        return when {
            isSwapped -> WordFeedback.SWAPPED
            score >= config.partialThreshold -> WordFeedback.PARTIAL
            else -> WordFeedback.PRONUNCIATION
        }
    }

    private fun buildPadaResults(mantra: Mantra, wordResults: List<WordResult>): List<PadaResult> =
        (1..mantra.padaCount).map { pada ->
            val padaWords = mantra.wordsForPada(pada).mapNotNull { wordResults.getOrNull(it.index) }
            PadaResult(
                pada = pada,
                coverage = padaWords.fractionOf { it.spokenWord != null },
                averageScore = padaWords.meanOf { it.score.toDouble() },
                wordResults = padaWords
            )
        }

    private inline fun <T> List<T>.fractionOf(predicate: (T) -> Boolean): Float =
        if (isEmpty()) 0.0f else count(predicate).toFloat() / size

    private inline fun <T> List<T>.meanOf(value: (T) -> Double): Float =
        if (isEmpty()) 0.0f else (sumOf(value) / size).toFloat()
}
