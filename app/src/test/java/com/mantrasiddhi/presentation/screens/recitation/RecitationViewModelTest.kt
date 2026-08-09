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

    private lateinit var viewModel: RecitationViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getCurrentUserUseCase = mockk(relaxed = true)
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(UserProfile(id = 1L, name = "Arjun", profileType = com.mantrasiddhi.domain.model.ProfileType.INDIVIDUAL, parentId = null, avatarIndex = 0))

        getActiveTargetUseCase = mockk(relaxed = true)
        coEvery { getActiveTargetUseCase(any()) } returns null

        getSessionsForUserUseCase = mockk(relaxed = true)
        coEvery { getSessionsForUserUseCase(any()) } returns flowOf(emptyList())

        speechRecognitionService = mockk(relaxed = true)
        evaluateRecitationUseCase = mockk(relaxed = true)
        detectRecitationBoundaryUseCase = mockk(relaxed = true)

        val soundEffectsPlayer = mockk<com.mantrasiddhi.data.audio.SoundEffectsPlayer>(relaxed = true)

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
            incrementActiveCloudGoalUseCase = mockk(relaxed = true),
            flushPurashcharanaSyncUseCase = mockk(relaxed = true)
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
            incrementActiveCloudGoalUseCase = mockk(relaxed = true),
            flushPurashcharanaSyncUseCase = mockk(relaxed = true)
        )

        assertTrue(errorViewModel.uiState.value is RecitationUiState.Error)
    }
}
