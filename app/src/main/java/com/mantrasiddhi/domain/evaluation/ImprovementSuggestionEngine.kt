/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.model.RecitationSession

/**
 * Engine that analyzes a user's recitation history or single session
 * to identify pronunciation patterns and generate targeted suggestions.
 */
object ImprovementSuggestionEngine {

    private val WORD_SUGGESTIONS = mapOf(
        "om" to "Ensure 'om' is chanted with a deep, resonant nasal sound (A-U-M).",
        "bhur" to "Pronounce 'bhur' with a strong, rolled 'r' and a short 'u' sound.",
        "bhuvah" to "Ensure the 'bhu' is aspirated (breath out) and the 'vah' ends with a soft breath.",
        "svah" to "The ending 'ah' (visarga) in 'svah' should sound like a gentle echo 'aha'.",
        "tatsavitur" to "Pronounce 'tatsavitur' as one word, keeping the 't' dental and emphasizing the 'vi' and 'tur'.",
        "varenyam" to "Break down 'varenyam' as va-re-nyam, pronouncing the 'nyam' clearly.",
        "bhargo" to "Ensure the 'bh' is aspirated and the 'g' is soft, not hard.",
        "devasya" to "Emphasize the 'de' sound and blend 'sya' smoothly at the end.",
        "dhimahi" to "Pronounce 'dhimahi' with a clear 'dh' sound (dental aspirated) and a soft 'hi'.",
        "dhiyo" to "Ensure the 'dh' sound in 'dhiyo' is clear and distinct from 'd'.",
        "yo" to "Chant 'yo' with a long vowel sound, clear and resonant.",
        "nah" to "The visarga 'ah' at the end of 'nah' should produce a soft 'aha' echo.",
        "prachodayat" to "Pronounce 'pra-cho-da-yat' slowly. The 't' at the end is half-pronounced and soft."
    )

    /**
     * Analyzes past sessions to find words that are consistently mispronounced (average score < 0.85).
     * Returns a list of improvement suggestions.
     *
     * @param sessions List of recent recitation sessions.
     * @param limit Maximum number of suggestions to return.
     * @return List of targeted improvement suggestions.
     */
    fun getSuggestions(sessions: List<RecitationSession>, limit: Int = 3): List<String> {
        if (sessions.isEmpty()) return emptyList()

        val wordTotals = mutableMapOf<String, Double>()
        val wordCounts = mutableMapOf<String, Int>()

        for (session in sessions) {
            for ((word, score) in session.wordScores) {
                wordTotals[word] = (wordTotals[word] ?: 0.0) + score
                wordCounts[word] = (wordCounts[word] ?: 0) + 1
            }
        }

        val lowScoringWords = wordTotals.mapNotNull { (word, totalScore) ->
            val count = wordCounts[word] ?: 1
            val average = totalScore / count
            if (average < 0.85) { // Any word average score < 0.85 needs practice
                word to average
            } else {
                null
            }
        }.sortedBy { it.second } // Sort worst scoring first

        return lowScoringWords.take(limit).map { (word, _) ->
            WORD_SUGGESTIONS[word] ?: "Focus on pronouncing '$word' more clearly."
        }
    }

    /**
     * Gets a specific pronunciation tip for a single word.
     *
     * @param word The target word.
     * @return A helpful suggestion message.
     */
    fun getSuggestionForWord(word: String): String {
        return WORD_SUGGESTIONS[word.lowercase().trim()] ?: "Focus on pronouncing '$word' more clearly."
    }
}
