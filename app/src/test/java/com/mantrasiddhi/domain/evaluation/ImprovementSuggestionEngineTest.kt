/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImprovementSuggestionEngineTest {

    @Test
    fun should_returnEmptySuggestions_when_noSessionsProvided() {
        val suggestions = ImprovementSuggestionEngine.getSuggestions(emptyList())
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun should_returnSuggestions_when_lowScoringWordsExist() {
        val sessions = listOf(
            RecitationSession(
                userId = 1,
                mode = RecitationMode.PRACTICE,
                overallScore = 0.70,
                wordScores = mapOf(
                    "varenyam" to 0.40,
                    "dhimahi" to 0.50,
                    "om" to 0.95
                ),
                transcription = "om ...",
                isCorrect = false,
                durationMs = 5000
            )
        )
        val suggestions = ImprovementSuggestionEngine.getSuggestions(sessions)
        
        // Should have suggestions for the two low-scoring words: varenyam and dhimahi
        assertEquals(2, suggestions.size)
        
        // Verify we get tips for varenyam or dhimahi
        val hasVarenyamTip = suggestions.any { it.contains("varenyam") }
        val hasDhimahiTip = suggestions.any { it.contains("dhimahi") }
        
        assertTrue(hasVarenyamTip)
        assertTrue(hasDhimahiTip)
    }

    @Test
    fun should_returnSingleWordSuggestion_when_wordRequested() {
        val suggestion = ImprovementSuggestionEngine.getSuggestionForWord("varenyam")
        assertTrue(suggestion.contains("varenyam"))
    }
}
