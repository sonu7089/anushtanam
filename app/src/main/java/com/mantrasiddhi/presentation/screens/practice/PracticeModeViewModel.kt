/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.audio.AudioCaptureService
import com.mantrasiddhi.data.audio.BenchmarkAudioPlayer
import com.mantrasiddhi.data.audio.RecordedAudioPlayer
import com.mantrasiddhi.data.speech.SpeechRecognitionService
import com.mantrasiddhi.data.speech.SpeechResult
import com.mantrasiddhi.domain.evaluation.MantraEvaluation
import com.mantrasiddhi.domain.evaluation.acoustic.RecordedAudio
import com.mantrasiddhi.domain.evaluation.acoustic.SelfAudioSegmenter
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.data.audio.SoundEffectsPlayer
import com.mantrasiddhi.domain.usecase.DetectRecitationBoundaryUseCase
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
    private val soundEffectsPlayer: SoundEffectsPlayer,
    private val audioCaptureService: AudioCaptureService,
    private val recordedAudioPlayer: RecordedAudioPlayer,
    private val detectRecitationBoundaryUseCase: DetectRecitationBoundaryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Idle)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private val currentUser = getCurrentUserUseCase()

    // Expose benchmark audio states
    val isPlayingBenchmark = benchmarkAudioPlayer.isPlaying
    val benchmarkPositionMs = benchmarkAudioPlayer.currentPositionMs
    val activeBenchmarkWordIndex = benchmarkAudioPlayer.activeWordIndex

    private val _hasSelfAudio = MutableStateFlow(false)

    /** Whether a captured recording from the last attempt is available for A/B playback. */
    val hasSelfAudio: StateFlow<Boolean> = _hasSelfAudio.asStateFlow()

    /** The learner's last captured recitation, retained for "hear yourself" playback. */
    private var lastAudio: RecordedAudio? = null

    /** Benchmark per-word durations (ms), used to slice the learner's own recording. */
    private val benchmarkWordDurationsMs: List<Double> =
        GayatriMantra.words.map { (it.benchmarkEndMs - it.benchmarkStartMs).toDouble() }

    private var recordingJob: Job? = null
    private var startTimeMs = 0L

    /**
     * Most recent transcript the recogniser produced this attempt. Tracked as a field
     * so both the silence watchdog and a manual stop evaluate the same text.
     */
    private var accumulatedText = ""

    /** Wall-clock time of the last non-blank speech result, for silence detection. */
    private var lastSpeechTimeMs = 0L

    /** Guards [finalizeRecording] so an attempt is only evaluated once. */
    private var finalizing = false

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
     * Plays a segment of the benchmark audio containing the selected word and its adjacent words.
     */
    fun playBenchmarkSegment(wordIndex: Int) {
        soundEffectsPlayer.triggerLightHaptic()
        // Stop recording if active
        if (_uiState.value is PracticeUiState.Recording) {
            stopRecording()
        }
        benchmarkAudioPlayer.playSegment(wordIndex)
    }

    /**
     * Plays *exactly* the selected reference word from the benchmark recording so the
     * learner can hear just that word (Phase 4.2 "tap to hear").
     */
    fun playReferenceWord(wordIndex: Int) {
        soundEffectsPlayer.triggerLightHaptic()
        recordedAudioPlayer.stop()
        if (_uiState.value is PracticeUiState.Recording) {
            stopRecording()
        }
        benchmarkAudioPlayer.playWord(wordIndex)
    }

    /**
     * Plays back the learner's own recording of the selected word for A/B comparison
     * with the reference. Falls back to the whole recording when the word segment
     * cannot be isolated. No-op when no audio buffer was captured.
     */
    fun playSelfWord(wordIndex: Int) {
        val audio = lastAudio ?: return
        soundEffectsPlayer.triggerLightHaptic()
        benchmarkAudioPlayer.pause()
        val range = SelfAudioSegmenter.sampleRangeFor(audio, benchmarkWordDurationsMs, wordIndex)
        if (range != null) {
            recordedAudioPlayer.play(audio, range)
        } else {
            recordedAudioPlayer.play(audio)
        }
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
        accumulatedText = ""
        finalizing = false
        lastSpeechTimeMs = System.currentTimeMillis()
        _uiState.value = PracticeUiState.Recording("", 0)
        startTimeMs = System.currentTimeMillis()
        // NOTE: We intentionally do NOT capture raw audio in parallel here. On many
        // devices a second AudioRecord session starves SpeechRecognizer of mic input,
        // so no transcript is ever produced and the attempt never finalises. The
        // recogniser owns the mic exclusively (mirroring Recitation Mode), and the
        // evaluation runs on the transcript text. The acoustic "hear yourself" / swara
        // path is sacrificed for reliable feedback.

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

            // Silence watchdog: the on-device recogniser frequently never emits a
            // Final result while listening continuously, so we detect the end of the
            // recitation ourselves and finalise automatically. The decision is a plain
            // elapsed-silence timeout because the recogniser returns Devanagari
            // (hi-IN) text, which DetectRecitationBoundaryUseCase's ASCII-only
            // normaliser would strip to empty — that use case can only catch the
            // Latin "prachodayat" closing word as an early trigger, never the pause.
            val watchdogJob = launch {
                while (isActive) {
                    delay(200)
                    val silence = System.currentTimeMillis() - lastSpeechTimeMs
                    val hasSpeech = accumulatedText.isNotBlank()
                    val recitationEnded = hasSpeech &&
                        (silence >= SILENCE_TIMEOUT_MS ||
                            detectRecitationBoundaryUseCase(accumulatedText, silence))
                    if (recitationEnded) {
                        timerJob.cancel()
                        finalizeRecording(accumulatedText)
                        break
                    }
                }
            }

            speechRecognitionService.startListening()
                .flowOn(Dispatchers.Main)
                .catch { e ->
                    timerJob.cancel()
                    watchdogJob.cancel()
                    _uiState.value = PracticeUiState.Error(e.message ?: "Speech recognition failed")
                }
                .collect { result ->
                    when (result) {
                        is SpeechResult.Partial -> {
                            if (result.text.isNotBlank()) {
                                accumulatedText = result.text
                                lastSpeechTimeMs = System.currentTimeMillis()
                            }
                            val current = _uiState.value
                            if (current is PracticeUiState.Recording) {
                                _uiState.value = current.copy(partialText = result.text)
                            }
                        }
                        is SpeechResult.Final -> {
                            timerJob.cancel()
                            watchdogJob.cancel()
                            val text = result.text.ifBlank { accumulatedText }
                            finalizeRecording(text, result.confidence, result.alternatives)
                        }
                        is SpeechResult.Error -> {
                            timerJob.cancel()
                            watchdogJob.cancel()
                            // A trailing recogniser error/timeout after the user has
                            // already recited should still score what we heard rather
                            // than discard the attempt.
                            if (accumulatedText.isNotBlank()) {
                                finalizeRecording(accumulatedText)
                            } else {
                                _uiState.value = PracticeUiState.Error(result.message)
                            }
                        }
                    }
                }
        }
    }

    /**
     * Stops recording and evaluates whatever transcript was captured so far.
     */
    fun stopRecording() {
        if (_uiState.value is PracticeUiState.Recording) {
            soundEffectsPlayer.triggerLightHaptic()
            finalizeRecording(accumulatedText)
        }
    }

    /**
     * Ends the current attempt exactly once: tears down the speech flow and either
     * evaluates the transcript or returns to idle when nothing was heard. Idempotent
     * via [finalizing] so the silence watchdog, a Final result, and a manual stop
     * cannot triple-evaluate the same attempt. Audio is null because Practice no
     * longer captures raw audio in parallel (it starved the recogniser); evaluation
     * runs on the transcript text.
     */
    private fun finalizeRecording(
        text: String,
        confidence: Float = 1.0f,
        alternatives: List<Pair<String, Float>> = emptyList()
    ) {
        if (finalizing) return
        finalizing = true
        recordingJob?.cancel()
        recordingJob = null
        if (text.isNotBlank()) {
            evaluateTranscription(text, confidence, alternatives, audio = null)
        } else {
            _uiState.value = PracticeUiState.Idle
        }
    }

    /**
     * Resets the screen state back to idle.
     */
    fun resetPractice() {
        benchmarkAudioPlayer.stop()
        recordedAudioPlayer.stop()
        audioCaptureService.stop()
        recordingJob?.cancel()
        recordingJob = null
        accumulatedText = ""
        finalizing = false
        lastAudio = null
        _hasSelfAudio.value = false
        _uiState.value = PracticeUiState.Idle
    }

    private fun evaluateTranscription(
        transcription: String,
        confidence: Float = 1.0f,
        alternatives: List<Pair<String, Float>> = emptyList(),
        audio: RecordedAudio? = null
    ) {
        _uiState.value = PracticeUiState.Processing
        viewModelScope.launch {
            try {
                val userId = currentUser.value?.id ?: 1L
                val recitationTime = System.currentTimeMillis() - startTimeMs
                lastAudio = audio?.takeIf { !it.isEmpty }
                _hasSelfAudio.value = lastAudio != null
                val evaluation = evaluateRecitationUseCase(
                    userId = userId,
                    transcription = transcription,
                    recitationTimeMs = recitationTime,
                    mode = RecitationMode.PRACTICE,
                    alternatives = alternatives,
                    confidence = confidence,
                    audio = audio
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
        recordedAudioPlayer.release()
        audioCaptureService.release()
        recordingJob?.cancel()
    }

    private companion object {
        /**
         * How long the learner can pause after speech before the attempt is
         * auto-finalised and evaluated. Kept close to Recitation Mode's 2s gap so
         * natural in-mantra pauses don't cut a recitation short.
         */
        const val SILENCE_TIMEOUT_MS = 2000L
    }
}
