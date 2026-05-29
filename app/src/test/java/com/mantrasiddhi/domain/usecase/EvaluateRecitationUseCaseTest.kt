/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.repository.RecitationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EvaluateRecitationUseCaseTest {

    private lateinit var recitationRepository: RecitationRepository
    private lateinit var incrementTargetUseCase: IncrementTargetUseCase
    private lateinit var evaluateRecitationUseCase: EvaluateRecitationUseCase

    @BeforeEach
    fun setUp() {
        recitationRepository = mockk(relaxed = true)
        incrementTargetUseCase = mockk(relaxed = true)
        evaluateRecitationUseCase = EvaluateRecitationUseCase(
            recitationRepository,
            incrementTargetUseCase
        )
    }

    @Test
    fun `should_saveSessionAndNotIncrementTarget_when_modeIsPractice`() = runTest {
        // Given
        val correctInput = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        
        // When
        val evaluation = evaluateRecitationUseCase(
            userId = 1L,
            transcription = correctInput,
            recitationTimeMs = 5000L,
            mode = RecitationMode.PRACTICE
        )

        // Then
        assertTrue(evaluation.isCorrect)
        assertEquals(1.0f, evaluation.overallScore)
        coVerify(exactly = 1) { recitationRepository.saveSession(any()) }
        coVerify(exactly = 0) { incrementTargetUseCase(any()) }
    }

    @Test
    fun `should_saveSessionAndIncrementTarget_when_modeIsNormalAndRecitationCorrect`() = runTest {
        // Given
        val correctInput = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        
        // When
        val evaluation = evaluateRecitationUseCase(
            userId = 1L,
            transcription = correctInput,
            recitationTimeMs = 5000L,
            mode = RecitationMode.NORMAL
        )

        // Then
        assertTrue(evaluation.isCorrect)
        coVerify(exactly = 1) { recitationRepository.saveSession(any()) }
        coVerify(exactly = 1) { incrementTargetUseCase(1L) }
    }

    @Test
    fun `should_saveSessionAndNotIncrementTarget_when_modeIsNormalAndRecitationIncorrect`() = runTest {
        // Given
        val incorrectInput = "om bhur bhuvah svah"
        
        // When
        val evaluation = evaluateRecitationUseCase(
            userId = 1L,
            transcription = incorrectInput,
            recitationTimeMs = 3000L,
            mode = RecitationMode.NORMAL
        )

        // Then
        assertFalse(evaluation.isCorrect)
        coVerify(exactly = 1) { recitationRepository.saveSession(any()) }
        coVerify(exactly = 0) { incrementTargetUseCase(any()) }
    }
}
