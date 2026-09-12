/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.practice

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.data.audio.AudioCaptureService
import com.mantrasiddhi.data.audio.BenchmarkAudioPlayer
import com.mantrasiddhi.data.audio.RecordedAudioPlayer
import com.mantrasiddhi.data.speech.SpeechRecognitionService
import com.mantrasiddhi.data.speech.RecitationClock
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
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

    /** @property messageResId copy to show; the ViewModel never builds display text itself. */
    data class Error(@StringRes val messageResId: Int) : PracticeUiState
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
    private val detectRecitationBoundaryUseCase: DetectRecitationBoundaryUseCase,
    private val clock: RecitationClock = RecitationClock()
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
    private var evaluationJob: Job? = null
    private var speechEndedAtMs: Long? = null
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

    /** Navigation/lifecycle cleanup must not vibrate or interfere with speech finalization. */
    fun onPlaybackHidden() {
        benchmarkAudioPlayer.pause()
        recordedAudioPlayer.stop()
    }

    /**
     * Starts recording practice recitation.
     */
    fun startRecording() {
        if (_uiState.value is PracticeUiState.Recording || _uiState.value is PracticeUiState.Processing) return
        evaluationJob?.cancel()
        recordedAudioPlayer.stop()
        lastAudio = null
        _hasSelfAudio.value = false
        speechEndedAtMs = null
        soundEffectsPlayer.triggerLightHaptic()
        benchmarkAudioPlayer.stop()
        recordingJob?.cancel()
        accumulatedText = ""
        finalizing = false
        lastSpeechTimeMs = clock.nowMs()
        _uiState.value = PracticeUiState.Recording("", 0)
        startTimeMs = clock.nowMs()
        // NOTE: We intentionally do NOT capture raw audio in parallel here. On many
        // devices a second AudioRecord session starves SpeechRecognizer of mic input,
        // so no transcript is ever produced and the attempt never finalises. The
        // recogniser owns the mic exclusively (mirroring Recitation Mode), and the
        // evaluation runs on the transcript text. The acoustic "hear yourself" / swara
        // path is sacrificed for reliable feedback.

        recordingJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
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

            // Changed text is the fallback activity signal; repeated hypotheses must
            // not keep the microphone open forever. Give a provider's final result a
            // short grace period after it reports end-of-speech.
            val watchdogJob = launch {
                while (isActive) {
                    delay(200)
                    val silence = clock.nowMs() - lastSpeechTimeMs
                    val hasSpeech = accumulatedText.isNotBlank()
                    val finalWaitExpired = speechEndedAtMs?.let { clock.nowMs() - it >= FINAL_RESULT_TIMEOUT_MS } == true
                    val recitationEnded = hasSpeech &&
                        (finalWaitExpired || silence >= SILENCE_TIMEOUT_MS ||
                            detectRecitationBoundaryUseCase(accumulatedText, silence))
                    if (recitationEnded) {
                        timerJob.cancel()
                        finalizeRecording(accumulatedText)
                        break
                    }
                    if ((!hasSpeech && clock.nowMs() - startTimeMs >= NO_SPEECH_TIMEOUT_MS) ||
                        (!hasSpeech && finalWaitExpired) ||
                        clock.nowMs() - startTimeMs >= MAX_RECORDING_MS) {
                        if (hasSpeech) finalizeRecording(accumulatedText)
                        else {
                            finalizeRecording("")
                            _uiState.value = PracticeUiState.Error(R.string.speech_error_speech_timeout)
                        }
                        break
                    }
                }
            }

            speechRecognitionService.startListening()
                .flowOn(Dispatchers.Main)
                .catch {
                    timerJob.cancel()
                    watchdogJob.cancel()
                    finalizeRecording("")
                    _uiState.value = PracticeUiState.Error(R.string.practice_error_speech_failed)
                }
                .collect { result ->
                    when (result) {
                        SpeechResult.EndOfSpeech -> if (speechEndedAtMs == null) { speechEndedAtMs = clock.nowMs() }
                        is SpeechResult.Partial -> {
                            if (result.text.isNotBlank() && result.text != accumulatedText) {
                                accumulatedText = result.text
                                lastSpeechTimeMs = clock.nowMs()
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
                                finalizeRecording("")
                                _uiState.value = PracticeUiState.Error(result.messageResId)
                            }
                        }
                    }
                }
            if (!finalizing) {
                if (accumulatedText.isNotBlank()) finalizeRecording(accumulatedText)
                else {
                    finalizeRecording("")
                    _uiState.value = PracticeUiState.Error(R.string.speech_error_no_match)
                }
            }
        }
        // Install the job before synchronous callbacks can finalize and cancel it.
        recordingJob?.start()
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
        evaluationJob?.cancel()
        evaluationJob = null
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
        evaluationJob = viewModelScope.launch {
            try {
                val userId = currentUser.value?.id ?: 1L
                val recitationTime = clock.nowMs() - startTimeMs
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
                // Practice mode intentionally gives no per-attempt audio feedback;
                // learners get on-screen results instead. Button haptics remain.
                coroutineContext.ensureActive()
                _uiState.value = PracticeUiState.ShowingResults(evaluation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.value = PracticeUiState.Error(R.string.practice_error_evaluate_failed)
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
        const val FINAL_RESULT_TIMEOUT_MS = 1500L
        const val NO_SPEECH_TIMEOUT_MS = 15000L
        const val MAX_RECORDING_MS = 120000L
    }
}
