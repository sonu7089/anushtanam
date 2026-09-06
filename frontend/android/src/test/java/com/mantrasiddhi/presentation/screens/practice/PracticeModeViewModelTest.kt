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
import com.mantrasiddhi.data.speech.RecitationClock
import com.mantrasiddhi.data.speech.SpeechResult
import com.mantrasiddhi.domain.evaluation.MantraEvaluation
import com.mantrasiddhi.resources.R
import io.mockk.every
import io.mockk.coVerify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
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
        coEvery { evaluateRecitationUseCase(any(), any(), any(), any(), any(), any(), any()) } returns
            MantraEvaluation(1f, emptyList(), true, emptyList(), emptyList(), 5000L)
        
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
            recordedAudioPlayer = recordedAudioPlayer,
            detectRecitationBoundaryUseCase = DetectRecitationBoundaryUseCase(),
            clock = mockk<RecitationClock> { every { nowMs() } answers { testDispatcher.scheduler.currentTime } }
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
    @Test
    fun `silent recognizer times out instead of listening forever`() = runTest {
        every { speechRecognitionService.startListening() } returns flow { awaitCancellation() }
        viewModel.startRecording()
        advanceTimeBy(15200); runCurrent()
        assertEquals(PracticeUiState.Error(R.string.speech_error_speech_timeout), viewModel.uiState.value)
        coVerify(exactly = 0) { evaluateRecitationUseCase(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `identical partial results do not postpone completion`() = runTest {
        every { speechRecognitionService.startListening() } returns flow {
            while (true) { emit(SpeechResult.Partial("om bhur bhuvah")); delay(100) }
        }
        viewModel.startRecording()
        advanceTimeBy(2200); runCurrent()
        assertTrue(viewModel.uiState.value is PracticeUiState.ShowingResults)
        coVerify(exactly = 1) { evaluateRecitationUseCase(any(), "om bhur bhuvah", any(), any(), any(), any(), any()) }
    }

    @Test
    fun `closing word finalizes within a watchdog tick`() = runTest {
        every { speechRecognitionService.startListening() } returns flow {
            emit(SpeechResult.Partial("ॐ भूर्भुवः स्वः प्रचोदयात्")); awaitCancellation()
        }
        viewModel.startRecording()
        advanceTimeBy(200); runCurrent()
        assertTrue(viewModel.uiState.value is PracticeUiState.ShowingResults)
        viewModel.stopRecording()
        coVerify(exactly = 1) { evaluateRecitationUseCase(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `final result inside grace period retains confidence and alternatives`() = runTest {
        val alternatives = listOf("om bhur" to 0.4f)
        every { speechRecognitionService.startListening() } returns flow {
            emit(SpeechResult.Partial("om"))
            emit(SpeechResult.EndOfSpeech)
            delay(500)
            emit(SpeechResult.Final("om bhur", 0.4f, alternatives))
        }
        viewModel.startRecording()
        advanceTimeBy(600); runCurrent()
        coVerify(exactly = 1) { evaluateRecitationUseCase(any(), "om bhur", any(), any(), alternatives, 0.4f, null) }
    }

    @Test
    fun `end of speech without final callback uses bounded fallback`() = runTest {
        every { speechRecognitionService.startListening() } returns flow {
            emit(SpeechResult.Partial("om bhur"))
            emit(SpeechResult.EndOfSpeech)
            awaitCancellation()
        }
        viewModel.startRecording()
        advanceTimeBy(1600); runCurrent()
        assertTrue(viewModel.uiState.value is PracticeUiState.ShowingResults)
    }

    @Test
    fun `provider closing without callbacks exits recording`() = runTest {
        every { speechRecognitionService.startListening() } returns emptyFlow()
        viewModel.startRecording(); runCurrent()
        assertEquals(PracticeUiState.Error(R.string.speech_error_no_match), viewModel.uiState.value)
    }

    @Test
    fun `reset cancels pending evaluation and does not publish stale results`() = runTest {
        every { speechRecognitionService.startListening() } returns flow {
            emit(SpeechResult.Final("om", 1f))
        }
        coEvery { evaluateRecitationUseCase(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            delay(5000)
            MantraEvaluation(1f, emptyList(), true, emptyList(), emptyList(), 5000L)
        }
        viewModel.startRecording(); runCurrent()
        assertTrue(viewModel.uiState.value is PracticeUiState.Processing)
        viewModel.resetPractice()
        advanceTimeBy(6000); runCurrent()
        assertEquals(PracticeUiState.Idle, viewModel.uiState.value)
    }

}
