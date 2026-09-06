/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
import com.mantrasiddhi.domain.model.LearnerLevel
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.mantrasiddhi.domain.repository.RecitationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class DetermineLearnerLevelUseCaseTest {

    private fun sessions(count: Int, score: Double): List<RecitationSession> =
        (1..count).map { index ->
            RecitationSession(
                id = index.toLong(),
                userId = 1L,
                mantraId = gayatri.id,
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
    @Test
    fun should_useOnlyRecentWindow_withoutChangingLevelForLargeHistory() = runTest {
        val history = sessions(5000, 0.95) + sessions(20, 0.80).map { it.copy(createdAt = it.createdAt + 5000) }
        val repository = mockk<RecitationRepository>()
        every { repository.getRecentSessionsForUser(1L, 20) } returns
            flowOf(history.sortedByDescending { it.createdAt }.take(20))
        assertEquals(DetermineLearnerLevelUseCase.levelFor(history), DetermineLearnerLevelUseCase(repository)(1L))
        verify(exactly = 0) { repository.getSessionsForUser(any()) }
    }

}
