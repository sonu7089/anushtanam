/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import kotlin.math.max

/**
 * Core evaluation engine for scoring Gayatri Mantra recitation accuracy.
 */
object MantraEvaluator {

    /**
     * Canonical reference words of the Gayatri Mantra in transliterated form.
     */
    val REFERENCE_WORDS = listOf(
        "om", "bhur", "bhuvah", "svah",
        "tatsavitur", "varenyam",
        "bhargo", "devasya", "dhimahi",
        "dhiyo", "yo", "nah", "prachodayat"
    )

    private const val CORRECT_THRESHOLD = 0.85f
    private const val SWAP_THRESHOLD = 0.85f
    private const val FEEDBACK_THRESHOLD = 0.70f
    private const val WORD_SCORE_MID = 0.40f

    /**
     * Evaluates the spoken transcription against the reference mantra.
     *
     * @param transcription Raw text output from speech recognition.
     * @param recitationTimeMs Time taken in milliseconds for the recitation.
     * @return Evaluation details including word breakdown and overall score.
     */
    fun evaluate(transcription: String, recitationTimeMs: Long): MantraEvaluation {
        val normalizedInput = TextNormalizer.normalize(transcription)
        val spokenWords = if (normalizedInput.isEmpty()) {
            emptyList()
        } else {
            normalizedInput.split(" ")
        }

        val alignment = LevenshteinAligner.align(REFERENCE_WORDS, spokenWords)
        val wordResults = mutableListOf<WordResult>()
        val extraWords = mutableListOf<String>()

        evaluateWords(alignment, spokenWords, wordResults, extraWords)

        val overallScore = calculateOverallScore(wordResults)
        val isCorrect = overallScore >= CORRECT_THRESHOLD
        val missingWords = wordResults.filter { it.spokenWord == null }.map { it.referenceWord }

        return MantraEvaluation(
            overallScore = overallScore,
            wordResults = wordResults,
            isCorrect = isCorrect,
            missingWords = missingWords,
            extraWords = extraWords,
            recitationTimeMs = recitationTimeMs
        )
    }

    private fun evaluateWords(
        alignment: List<AlignmentStep>,
        spokenWords: List<String>,
        wordResults: MutableList<WordResult>,
        extraWords: MutableList<String>
    ) {
        val stepMap = alignment.filter { it.refIndex != null }.associateBy { it.refIndex!! }
        
        for (i in REFERENCE_WORDS.indices) {
            val step = stepMap[i]
            if (step != null && step.spokenIndex != null) {
                val spokenWord = spokenWords[step.spokenIndex]
                val score = LevenshteinAligner.wordSimilarity(REFERENCE_WORDS[i], spokenWord).toFloat()
                val feedback = determineFeedback(spokenWord, score, i)
                wordResults.add(WordResult(REFERENCE_WORDS[i], spokenWord, score, feedback))
            } else {
                wordResults.add(WordResult(REFERENCE_WORDS[i], null, 0.0f, WordFeedback.MISSING))
            }
        }

        alignment.filter { it.refIndex == null && it.spokenIndex != null }.forEach {
            extraWords.add(spokenWords[it.spokenIndex!!])
        }
    }

    private fun determineFeedback(
        spokenWord: String,
        score: Float,
        refIndex: Int
    ): WordFeedback? {
        if (score >= FEEDBACK_THRESHOLD) return null

        // Check if the spoken word was actually a swapped word from elsewhere in the mantra
        val isSwapped = REFERENCE_WORDS.indices.any { k ->
            k != refIndex && LevenshteinAligner.wordSimilarity(REFERENCE_WORDS[k], spokenWord) >= SWAP_THRESHOLD
        }

        return when {
            isSwapped -> WordFeedback.SWAPPED
            score >= WORD_SCORE_MID -> WordFeedback.PARTIAL
            else -> WordFeedback.PRONUNCIATION
        }
    }

    private fun calculateOverallScore(wordResults: List<WordResult>): Float {
        if (wordResults.isEmpty()) return 0.0f
        val total = wordResults.sumOf { it.score.toDouble() }
        return (total / wordResults.size).toFloat()
    }
}
