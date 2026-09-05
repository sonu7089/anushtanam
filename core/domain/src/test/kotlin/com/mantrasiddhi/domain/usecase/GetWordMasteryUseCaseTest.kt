/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.RecitationRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetWordMasteryUseCaseTest {

    private lateinit var recitationRepository: RecitationRepository
    private lateinit var getWordMasteryUseCase: GetWordMasteryUseCase

    @BeforeEach
    fun setUp() {
        recitationRepository = mockk()
        getWordMasteryUseCase = GetWordMasteryUseCase(recitationRepository)
    }

    @Test
    fun `should_calculateWordMasteryCorrectly_when_sessionsExist`() = runTest {
        // Given
        val userId = 1L
        val sessions = listOf(
            RecitationSession(
                id = 1,
                userId = userId,
                mode = RecitationMode.NORMAL,
                overallScore = 0.90,
                wordScores = mapOf("om" to 1.0, "bhur" to 0.8, "bhuvah" to 0.5),
                transcription = "om bhur bhuvah",
                isCorrect = true,
                durationMs = 5000L
            ),
            RecitationSession(
                id = 2,
                userId = userId,
                mode = RecitationMode.NORMAL,
                overallScore = 0.95,
                wordScores = mapOf("om" to 1.0, "bhur" to 0.9, "bhuvah" to 0.7),
                transcription = "om bhur bhuvah",
                isCorrect = true,
                durationMs = 5000L
            )
        )
        every { recitationRepository.getSessionsForUser(userId) } returns flowOf(sessions)

        // When
        val result = getWordMasteryUseCase(userId).first()

        // Then
        val omMastery = result.find { it.word == "om" }
        val bhurMastery = result.find { it.word == "bhur" }
        val bhuvahMastery = result.find { it.word == "bhuvah" }

        assertEquals(1.0, omMastery?.averageScore ?: 0.0, 0.0001)
        assertEquals(2, omMastery?.totalAttempts)

        assertEquals(0.85, bhurMastery?.averageScore ?: 0.0, 0.0001)
        assertEquals(2, bhurMastery?.totalAttempts)

        assertEquals(0.6, bhuvahMastery?.averageScore ?: 0.0, 0.0001)
        assertEquals(2, bhuvahMastery?.totalAttempts)


    }
}
