/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
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

class GetSessionsForUserUseCaseTest {

    private lateinit var recitationRepository: RecitationRepository
    private lateinit var getSessionsForUserUseCase: GetSessionsForUserUseCase

    @BeforeEach
    fun setUp() {
        recitationRepository = mockk()
        getSessionsForUserUseCase = GetSessionsForUserUseCase(recitationRepository)
    }

    @Test
    fun `should_returnSessions_when_userHasSessions`() = runTest {
        // Given
        val sessions = listOf(
            RecitationSession(
                id = 1,
                userId = 2,
                mode = RecitationMode.NORMAL,
                overallScore = 0.96,
                wordScores = mapOf("om" to 1.0),
                transcription = "om",
                isCorrect = true,
                durationMs = 5000L
            )
        )
        every { recitationRepository.getSessionsForUser(2L) } returns flowOf(sessions)

        // When
        val result = getSessionsForUserUseCase(2L).first()

        // Then
        assertEquals(1, result.size)
        assertEquals(0.96, result[0].overallScore)
    }
}
