/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.practice

import com.mantrasiddhi.data.audio.BenchmarkAudioPlayer
import com.mantrasiddhi.data.speech.SpeechRecognitionService
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.DetectRecitationBoundaryUseCase
import com.mantrasiddhi.domain.usecase.EvaluateRecitationUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeModeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var speechRecognitionService: SpeechRecognitionService
    private lateinit var evaluateRecitationUseCase: EvaluateRecitationUseCase
    private lateinit var benchmarkAudioPlayer: BenchmarkAudioPlayer

    private lateinit var viewModel: PracticeModeViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        getCurrentUserUseCase = mockk(relaxed = true)
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(UserProfile(id = 1L, name = "Arjun", profileType = com.mantrasiddhi.domain.model.ProfileType.INDIVIDUAL, parentId = null, avatarIndex = 0))

        speechRecognitionService = mockk(relaxed = true)
        evaluateRecitationUseCase = mockk(relaxed = true)
        benchmarkAudioPlayer = mockk(relaxed = true)
        
        val soundEffectsPlayer = mockk<com.mantrasiddhi.data.audio.SoundEffectsPlayer>(relaxed = true)
        val audioCaptureService = mockk<com.mantrasiddhi.data.audio.AudioCaptureService>(relaxed = true)
        val recordedAudioPlayer = mockk<com.mantrasiddhi.data.audio.RecordedAudioPlayer>(relaxed = true)

        viewModel = PracticeModeViewModel(
            speechRecognitionService = speechRecognitionService,
            benchmarkAudioPlayer = benchmarkAudioPlayer,
            evaluateRecitationUseCase = evaluateRecitationUseCase,
            getCurrentUserUseCase = getCurrentUserUseCase,
            soundEffectsPlayer = soundEffectsPlayer,
            audioCaptureService = audioCaptureService,
            recordedAudioPlayer = recordedAudioPlayer
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should_initiallyEmitIdleState_when_created`() = runTest {
        assertTrue(viewModel.uiState.value is PracticeUiState.Idle)
    }
}
