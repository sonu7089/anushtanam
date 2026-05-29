/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DetectRecitationBoundaryUseCaseTest {

    private lateinit var useCase: DetectRecitationBoundaryUseCase

    @BeforeEach
    fun setUp() {
        useCase = DetectRecitationBoundaryUseCase()
    }

    @Test
    fun `should_returnTrue_when_finalWordSpoken`() {
        // Given
        val transcription1 = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val transcription2 = "dhiyo yo nah prachodayat"
        val transcription3 = "prachodayat"

        // Then
        assertTrue(useCase(transcription1, 0L))
        assertTrue(useCase(transcription2, 500L))
        assertTrue(useCase(transcription3, 1000L))
    }

    @Test
    fun `should_returnTrue_when_silenceThresholdExceeded`() {
        // Given
        val transcription = "om bhur bhuvah"
        val silenceMs = 2500L

        // Then
        assertTrue(useCase(transcription, silenceMs))
    }

    @Test
    fun `should_returnFalse_when_notFinalWordAndSilenceLow`() {
        // Given
        val transcription = "om bhur bhuvah"
        val silenceMs = 500L

        // Then
        assertFalse(useCase(transcription, silenceMs))
    }

    @Test
    fun `should_returnFalse_when_silenceExceededButTranscriptionEmpty`() {
        // Given
        val transcription = ""
        val silenceMs = 3000L

        // Then
        assertFalse(useCase(transcription, silenceMs))
    }
}
