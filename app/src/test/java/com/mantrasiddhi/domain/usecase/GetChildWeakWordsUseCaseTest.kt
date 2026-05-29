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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetChildWeakWordsUseCaseTest {

    private lateinit var recitationRepository: RecitationRepository
    private lateinit var getChildWeakWordsUseCase: GetChildWeakWordsUseCase

    @BeforeEach
    fun setUp() {
        recitationRepository = mockk()
        getChildWeakWordsUseCase = GetChildWeakWordsUseCase(recitationRepository)
    }

    @Test
    fun `should_returnEmptyList_when_noSessionsExist`() = runTest {
        // Given
        every { recitationRepository.getSessionsForUser(2L) } returns flowOf(emptyList())

        // When
        val result = getChildWeakWordsUseCase(2L).first()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should_returnThreeLowestScoringWords_when_sessionsExist`() = runTest {
        // Given
        val session1 = RecitationSession(
            id = 1,
            userId = 2L,
            mode = RecitationMode.NORMAL,
            overallScore = 0.9,
            transcription = "om bhur bhuvah",
            isCorrect = true,
            durationMs = 5000L,
            createdAt = 1000L,
            wordScores = mapOf(
                "om" to 0.5,
                "bhur" to 0.4,
                "bhuvah" to 0.9,
                "svah" to 1.0,
                "tatsavitur" to 1.0,
                "varenyam" to 1.0,
                "bhargo" to 0.3,
                "devasya" to 1.0,
                "dhimahi" to 1.0,
                "dhiyo" to 1.0,
                "yo" to 1.0,
                "nah" to 1.0,
                "prachodayat" to 1.0
            )
        )
        val session2 = RecitationSession(
            id = 2,
            userId = 2L,
            mode = RecitationMode.NORMAL,
            overallScore = 0.9,
            transcription = "om bhur bhuvah",
            isCorrect = true,
            durationMs = 5000L,
            createdAt = 2000L,
            wordScores = mapOf(
                "om" to 0.6,
                "bhur" to 0.2, // average = 0.3
                "bhuvah" to 0.9,
                "svah" to 1.0,
                "tatsavitur" to 1.0,
                "varenyam" to 1.0,
                "bhargo" to 0.5, // average = 0.4
                "devasya" to 1.0,
                "dhimahi" to 1.0,
                "dhiyo" to 1.0,
                "yo" to 1.0,
                "nah" to 1.0,
                "prachodayat" to 1.0
            )
        )
        every { recitationRepository.getSessionsForUser(2L) } returns flowOf(listOf(session1, session2))

        // When
        val result = getChildWeakWordsUseCase(2L).first()

        // Then
        // bhur (0.3), bhargo (0.4), om (0.55) are the lowest.
        assertEquals(3, result.size)
        assertEquals("bhur", result[0])
        assertEquals("bhargo", result[1])
        assertEquals("om", result[2])
    }
}
