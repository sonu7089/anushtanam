/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.audio.BenchmarkAudioPlayer
import com.mantrasiddhi.data.speech.SpeechRecognitionService
import com.mantrasiddhi.data.speech.SpeechResult
import com.mantrasiddhi.domain.evaluation.MantraEvaluation
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.data.audio.SoundEffectsPlayer
import com.mantrasiddhi.domain.usecase.EvaluateRecitationUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for the Practice Mode screen.
 */
sealed interface PracticeUiState {
    data object Idle : PracticeUiState
    data class Recording(val partialText: String, val secondsElapsed: Int) : PracticeUiState
    data object Processing : PracticeUiState
    data class ShowingResults(val evaluation: MantraEvaluation) : PracticeUiState
    data class Error(val message: String) : PracticeUiState
}

/**
 * ViewModel orchestrating user practice sessions, playing benchmark audio,
 * and performing real-time speech evaluation.
 */
@HiltViewModel
class PracticeModeViewModel @Inject constructor(
    private val speechRecognitionService: SpeechRecognitionService,
    private val benchmarkAudioPlayer: BenchmarkAudioPlayer,
    private val evaluateRecitationUseCase: EvaluateRecitationUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val soundEffectsPlayer: SoundEffectsPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Idle)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private val currentUser = getCurrentUserUseCase()

    // Expose benchmark audio states
    val isPlayingBenchmark = benchmarkAudioPlayer.isPlaying
    val benchmarkPositionMs = benchmarkAudioPlayer.currentPositionMs
    val activeBenchmarkWordIndex = benchmarkAudioPlayer.activeWordIndex

    private var recordingJob: Job? = null
    private var startTimeMs = 0L

    /**
     * Starts playing benchmark audio.
     */
    fun playBenchmark() {
        soundEffectsPlayer.triggerLightHaptic()
        // Stop recording if active
        if (_uiState.value is PracticeUiState.Recording) {
            stopRecording()
        }
        benchmarkAudioPlayer.play()
    }

    /**
     * Pauses benchmark audio.
     */
    fun pauseBenchmark() {
        soundEffectsPlayer.triggerLightHaptic()
        benchmarkAudioPlayer.pause()
    }

    /**
     * Starts recording practice recitation.
     */
    fun startRecording() {
        soundEffectsPlayer.triggerLightHaptic()
        benchmarkAudioPlayer.stop()
        recordingJob?.cancel()
        _uiState.value = PracticeUiState.Recording("", 0)
        startTimeMs = System.currentTimeMillis()

        recordingJob = viewModelScope.launch {
            val timerJob = launch {
                var seconds = 0
                while (isActive) {
                    delay(1000)
                    seconds++
                    val current = _uiState.value
                    if (current is PracticeUiState.Recording) {
                        _uiState.value = current.copy(secondsElapsed = seconds)
                    }
                }
            }

            speechRecognitionService.startListening()
                .flowOn(Dispatchers.Main)
                .catch { e ->
                    timerJob.cancel()
                    _uiState.value = PracticeUiState.Error(e.message ?: "Speech recognition failed")
                }
                .collect { result ->
                    when (result) {
                        is SpeechResult.Partial -> {
                            val current = _uiState.value
                            if (current is PracticeUiState.Recording) {
                                _uiState.value = current.copy(partialText = result.text)
                            }
                        }
                        is SpeechResult.Final -> {
                            timerJob.cancel()
                            evaluateTranscription(result.text)
                        }
                        is SpeechResult.Error -> {
                            timerJob.cancel()
                            _uiState.value = PracticeUiState.Error(result.message)
                        }
                    }
                }
        }
    }

    /**
     * Stops recording and evaluates whatever transcript was captured.
     */
    fun stopRecording() {
        val current = _uiState.value
        if (current is PracticeUiState.Recording) {
            soundEffectsPlayer.triggerLightHaptic()
            val text = current.partialText
            recordingJob?.cancel()
            recordingJob = null
            if (text.isNotBlank()) {
                evaluateTranscription(text)
            } else {
                _uiState.value = PracticeUiState.Idle
            }
        }
    }

    /**
     * Resets the screen state back to idle.
     */
    fun resetPractice() {
        benchmarkAudioPlayer.stop()
        recordingJob?.cancel()
        recordingJob = null
        _uiState.value = PracticeUiState.Idle
    }

    private fun evaluateTranscription(transcription: String) {
        _uiState.value = PracticeUiState.Processing
        viewModelScope.launch {
            try {
                val userId = currentUser.value?.id ?: 1L
                val recitationTime = System.currentTimeMillis() - startTimeMs
                val evaluation = evaluateRecitationUseCase(
                    userId = userId,
                    transcription = transcription,
                    recitationTimeMs = recitationTime,
                    mode = RecitationMode.PRACTICE
                )
                if (evaluation.isCorrect) {
                    soundEffectsPlayer.playCorrectSound()
                }
                _uiState.value = PracticeUiState.ShowingResults(evaluation)
            } catch (e: Exception) {
                _uiState.value = PracticeUiState.Error(e.message ?: "Failed to evaluate recitation")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        benchmarkAudioPlayer.release()
        recordingJob?.cancel()
    }
}
