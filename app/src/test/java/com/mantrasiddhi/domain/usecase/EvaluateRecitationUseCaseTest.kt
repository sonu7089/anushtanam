/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAnalyzer
import com.mantrasiddhi.domain.evaluation.acoustic.RecordedAudio
import com.mantrasiddhi.domain.evaluation.acoustic.SwaraAnalyzer
import com.mantrasiddhi.domain.evaluation.diagnostics.EvaluationDiagnostics
import com.mantrasiddhi.domain.model.LearnerLevel
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.repository.AppPreferencesRepository
import com.mantrasiddhi.domain.repository.ProgressSyncRepository
import com.mantrasiddhi.domain.repository.RecitationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EvaluateRecitationUseCaseTest {

    private lateinit var recitationRepository: RecitationRepository
    private lateinit var incrementTargetUseCase: IncrementTargetUseCase
    private lateinit var appPreferencesRepository: AppPreferencesRepository
    private lateinit var acousticAnalyzer: AcousticAnalyzer
    private lateinit var determineLearnerLevelUseCase: DetermineLearnerLevelUseCase
    private lateinit var evaluationDiagnostics: EvaluationDiagnostics
    private lateinit var swaraAnalyzer: SwaraAnalyzer
    private lateinit var progressSyncRepository: ProgressSyncRepository
    private lateinit var evaluateRecitationUseCase: EvaluateRecitationUseCase

    @BeforeEach
    fun setUp() {
        recitationRepository = mockk(relaxed = true)
        incrementTargetUseCase = mockk(relaxed = true)
        appPreferencesRepository = mockk()
        acousticAnalyzer = mockk(relaxed = true)
        determineLearnerLevelUseCase = mockk(relaxed = true)
        evaluationDiagnostics = mockk(relaxed = true)
        swaraAnalyzer = mockk(relaxed = true)
        progressSyncRepository = mockk(relaxed = true)
        // Explicit stub: relaxed mocks cannot synthesize a default kotlin.Result.
        coEvery {
            progressSyncRepository.recordSession(any(), any(), any())
        } returns Result.success(Unit)
        every { appPreferencesRepository.recitationSensitivity } returns flowOf(0.85)
        // Default: an explicit sensitivity is set, preserving the legacy gate behaviour.
        every { appPreferencesRepository.isRecitationSensitivityExplicit } returns flowOf(true)
        every { appPreferencesRepository.advancedVedicMode } returns flowOf(false)
        evaluateRecitationUseCase = EvaluateRecitationUseCase(
            recitationRepository,
            incrementTargetUseCase,
            appPreferencesRepository,
            acousticAnalyzer,
            determineLearnerLevelUseCase,
            evaluationDiagnostics,
            swaraAnalyzer,
            progressSyncRepository
        )
    }

    private fun useCase() = EvaluateRecitationUseCase(
        recitationRepository,
        incrementTargetUseCase,
        appPreferencesRepository,
        acousticAnalyzer,
        determineLearnerLevelUseCase,
        evaluationDiagnostics,
        swaraAnalyzer,
        progressSyncRepository
    )

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

    @Test
    fun `should_movePassBoundary_when_sensitivityChanged`() = runTest {
        // One scattered word missing: passes the completeness/correctness gates, so
        // the (sensitivity-driven) overall gate is what decides the verdict.
        val oneMissing = "om bhur bhuvah svah tatsavitur varenyam devasya dhimahi dhiyo yo nah prachodayat"

        // Lenient sensitivity 0.90: overall clears the gate -> passes.
        every { appPreferencesRepository.recitationSensitivity } returns flowOf(0.90)
        val lenient = useCase()
        val lenientResult = lenient(1L, oneMissing, 5000L, RecitationMode.PRACTICE)
        assertTrue(lenientResult.isCorrect, "should pass at sensitivity 0.90")

        // Strict sensitivity 0.999: overall no longer clears the gate -> fails.
        every { appPreferencesRepository.recitationSensitivity } returns flowOf(0.999)
        val strict = useCase()
        val strictResult = strict(1L, oneMissing, 5000L, RecitationMode.PRACTICE)
        assertFalse(strictResult.isCorrect, "should fail at sensitivity 0.999. Boundary moved.")
    }

    @Test
    fun `should_adaptGateToLearnerLevel_when_sensitivityNotExplicit`() = runTest {
        // No explicit sensitivity -> the gate auto-adapts to the learner's level.
        every { appPreferencesRepository.isRecitationSensitivityExplicit } returns flowOf(false)
        // One scattered word missing: completeness/correctness pass, the overall gate decides.
        val oneMissing = "om bhur bhuvah svah tatsavitur varenyam devasya dhimahi dhiyo yo nah prachodayat"

        // Beginner: lenient gate -> the same recitation passes.
        coEvery { determineLearnerLevelUseCase(1L) } returns LearnerLevel.BEGINNER
        val beginnerResult = useCase()(1L, oneMissing, 5000L, RecitationMode.PRACTICE)
        assertTrue(beginnerResult.isCorrect, "beginner should pass")

        // Advanced: strict gate -> the same recitation is flagged as needing work.
        coEvery { determineLearnerLevelUseCase(1L) } returns LearnerLevel.ADVANCED
        val advancedResult = useCase()(1L, oneMissing, 5000L, RecitationMode.PRACTICE)
        assertFalse(advancedResult.isCorrect, "advanced should be flagged for improvement")
    }

    @Test
    fun `should_notRunSwara_when_advancedModeOff`() = runTest {
        // Opt-in feature off: no pitch analysis runs and the result carries no swara.
        every { appPreferencesRepository.advancedVedicMode } returns flowOf(false)
        val input = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val audio = RecordedAudio(FloatArray(16_000) { 0.1f }, 16_000)

        val evaluation = useCase()(1L, input, 5000L, RecitationMode.PRACTICE, audio = audio)

        assertNull(evaluation.swara, "swara must be absent when Advanced/Vedic mode is off")
        coVerify(exactly = 0) { swaraAnalyzer.analyze(any(), any()) }
    }

    @Test
    fun `should_runSwara_when_advancedModeOnAndAudioPresent`() = runTest {
        every { appPreferencesRepository.advancedVedicMode } returns flowOf(true)
        val input = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        val audio = RecordedAudio(FloatArray(16_000) { 0.1f }, 16_000)

        useCase()(1L, input, 5000L, RecitationMode.PRACTICE, audio = audio)

        coVerify(exactly = 1) { swaraAnalyzer.analyze(any(), any()) }
    }
}
