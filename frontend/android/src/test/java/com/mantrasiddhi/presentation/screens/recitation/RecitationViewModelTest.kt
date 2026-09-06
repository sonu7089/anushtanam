/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.recitation

import com.mantrasiddhi.data.speech.SpeechRecognitionService
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.DetectRecitationBoundaryUseCase
import com.mantrasiddhi.domain.usecase.EvaluateRecitationUseCase
import com.mantrasiddhi.domain.usecase.GetActiveTargetUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.GetSessionsForUserUseCase
import com.mantrasiddhi.data.speech.SpeechResult
import com.mantrasiddhi.data.speech.RecitationClock
import com.mantrasiddhi.data.audio.SoundEffectsPlayer
import com.mantrasiddhi.domain.evaluation.MantraEvaluation
import com.mantrasiddhi.domain.usecase.IncrementActiveCloudGoalUseCase
import io.mockk.every
import io.mockk.verify
import io.mockk.coVerify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecitationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var getActiveTargetUseCase: GetActiveTargetUseCase
    private lateinit var getSessionsForUserUseCase: GetSessionsForUserUseCase
    private lateinit var speechRecognitionService: SpeechRecognitionService
    private lateinit var evaluateRecitationUseCase: EvaluateRecitationUseCase
    private lateinit var detectRecitationBoundaryUseCase: DetectRecitationBoundaryUseCase

    private lateinit var soundEffectsPlayer: SoundEffectsPlayer
    private lateinit var cloudGoal: IncrementActiveCloudGoalUseCase
    private lateinit var viewModel: RecitationViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getCurrentUserUseCase = mockk(relaxed = true)
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(UserProfile(id = 1L, name = "Arjun", profileType = com.mantrasiddhi.domain.model.ProfileType.INDIVIDUAL, parentId = null, avatarIndex = 0))

        getActiveTargetUseCase = mockk(relaxed = true)
        coEvery { getActiveTargetUseCase(any()) } returns null

        getSessionsForUserUseCase = mockk(relaxed = true)
        every { getSessionsForUserUseCase.recent(any(), any()) } returns flowOf(emptyList())

        speechRecognitionService = mockk(relaxed = true)
        evaluateRecitationUseCase = mockk(relaxed = true)
        detectRecitationBoundaryUseCase = mockk(relaxed = true)

        soundEffectsPlayer = mockk(relaxed = true)
        cloudGoal = mockk(relaxed = true)
        coEvery { cloudGoal() } returns Result.success(false)
        coEvery { evaluateRecitationUseCase(any(), any(), any(), any(), any(), any(), any()) } returns
            MantraEvaluation(1f, emptyList(), true, emptyList(), emptyList(), 5000L)

        viewModel = RecitationViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            getActiveTargetUseCase = getActiveTargetUseCase,
            createTargetUseCase = mockk(relaxed = true),
            getSessionsForUserUseCase = getSessionsForUserUseCase,
            speechRecognitionService = speechRecognitionService,
            evaluateRecitationUseCase = evaluateRecitationUseCase,
            detectRecitationBoundaryUseCase = detectRecitationBoundaryUseCase,
            soundEffectsPlayer = soundEffectsPlayer,
            feedbackStringProvider = mockk(relaxed = true),
            incrementActiveCloudGoalUseCase = cloudGoal,
            flushPurashcharanaSyncUseCase = mockk(relaxed = true),
            clock = mockk<RecitationClock> { every { nowMs() } answers { testDispatcher.scheduler.currentTime } }
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should_emitActiveState_when_userLoaded`() = runTest {
        assertTrue(viewModel.uiState.value is RecitationUiState.Active)
    }

    @Test
    fun `should_emitErrorState_when_noUserLoaded`() = runTest {
        val nullUserUseCase = mockk<GetCurrentUserUseCase>(relaxed = true)
        coEvery { nullUserUseCase() } returns MutableStateFlow(null)

        val soundEffectsPlayer = mockk<com.mantrasiddhi.data.audio.SoundEffectsPlayer>(relaxed = true)

        val errorViewModel = RecitationViewModel(
            getCurrentUserUseCase = nullUserUseCase,
            getActiveTargetUseCase = getActiveTargetUseCase,
            createTargetUseCase = mockk(relaxed = true),
            getSessionsForUserUseCase = getSessionsForUserUseCase,
            speechRecognitionService = speechRecognitionService,
            evaluateRecitationUseCase = evaluateRecitationUseCase,
            detectRecitationBoundaryUseCase = detectRecitationBoundaryUseCase,
            soundEffectsPlayer = soundEffectsPlayer,
            feedbackStringProvider = mockk(relaxed = true),
            incrementActiveCloudGoalUseCase = cloudGoal,
            flushPurashcharanaSyncUseCase = mockk(relaxed = true),
            clock = mockk<RecitationClock> { every { nowMs() } answers { testDispatcher.scheduler.currentTime } }
        )

        assertTrue(errorViewModel.uiState.value is RecitationUiState.Error)
    }
    @Test
    fun `back to back japa count immediately while cloud goal is stalled and stay silent`() = runTest {
        coEvery { cloudGoal() } coAnswers { awaitCancellation() }
        every { speechRecognitionService.startListening() } returns flow {
            emit(SpeechResult.Partial("om prachodayat"))
            delay(400)
            emit(SpeechResult.Partial("om prachodayat om prachodayat"))
            awaitCancellation()
        }
        viewModel.startRecitation()
        advanceTimeBy(500); runCurrent()
        assertEquals(2L, (viewModel.uiState.value as RecitationUiState.Active).japaCountedPulseId)
        coVerify(exactly = 2) { evaluateRecitationUseCase(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { soundEffectsPlayer.playCorrectSound() }
        verify(exactly = 0) { soundEffectsPlayer.playAchievementSound() }
        viewModel.stopRecitation()
        advanceTimeBy(17000); runCurrent()
    }

    @Test
    fun `repeated partial and final callbacks do not count the same completed mantra again`() = runTest {
        every { speechRecognitionService.startListening() } returns flow {
            emit(SpeechResult.Partial("om prachodayat"))
            delay(100)
            emit(SpeechResult.Partial("om prachodayat"))
            delay(100)
            emit(SpeechResult.Final("om prachodayat", 1f))
            awaitCancellation()
        }
        viewModel.startRecitation()
        advanceTimeBy(300); runCurrent()
        viewModel.stopRecitation()
        coVerify(exactly = 1) { evaluateRecitationUseCase(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `unchanged partials cannot starve the silence flush`() = runTest {
        every { speechRecognitionService.startListening() } returns flow {
            while (true) { emit(SpeechResult.Partial("om bhur")); delay(100) }
        }
        viewModel.startRecitation()
        advanceTimeBy(2200); runCurrent()
        assertEquals(1L, (viewModel.uiState.value as RecitationUiState.Active).japaCountedPulseId)
        viewModel.stopRecitation()
        coVerify(exactly = 1) { evaluateRecitationUseCase(any(), any(), any(), any(), any(), any(), any()) }
    }

}
