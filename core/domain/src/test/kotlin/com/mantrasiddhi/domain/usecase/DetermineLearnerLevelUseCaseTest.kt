/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.LearnerLevel
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DetermineLearnerLevelUseCaseTest {

    private fun sessions(count: Int, score: Double): List<RecitationSession> =
        (1..count).map { index ->
            RecitationSession(
                id = index.toLong(),
                userId = 1L,
                mode = RecitationMode.PRACTICE,
                overallScore = score,
                wordScores = emptyMap(),
                transcription = "",
                isCorrect = score >= 0.85,
                durationMs = 5000L,
                createdAt = index.toLong()
            )
        }

    @Test
    fun should_beBeginner_when_noHistory() {
        assertEquals(LearnerLevel.BEGINNER, DetermineLearnerLevelUseCase.levelFor(emptyList()))
    }

    @Test
    fun should_beBeginner_when_fewSessions() {
        assertEquals(LearnerLevel.BEGINNER, DetermineLearnerLevelUseCase.levelFor(sessions(3, 0.95)))
    }

    @Test
    fun should_beIntermediate_when_moderateAverage() {
        assertEquals(LearnerLevel.INTERMEDIATE, DetermineLearnerLevelUseCase.levelFor(sessions(10, 0.80)))
    }

    @Test
    fun should_beAdvanced_when_highAverageOverManySessions() {
        assertEquals(LearnerLevel.ADVANCED, DetermineLearnerLevelUseCase.levelFor(sessions(20, 0.93)))
    }

    @Test
    fun should_beBeginner_when_lowAverage() {
        assertEquals(LearnerLevel.BEGINNER, DetermineLearnerLevelUseCase.levelFor(sessions(10, 0.60)))
    }
}
