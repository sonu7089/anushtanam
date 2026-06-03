/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.recitation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.speech.SpeechRecognitionService
import com.mantrasiddhi.data.speech.SpeechResult
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.CreateTargetUseCase
import com.mantrasiddhi.domain.usecase.DetectRecitationBoundaryUseCase
import com.mantrasiddhi.domain.usecase.EvaluateRecitationUseCase
import com.mantrasiddhi.domain.usecase.GetActiveTargetUseCase
import com.mantrasiddhi.data.audio.SoundEffectsPlayer
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.GetSessionsForUserUseCase
import com.mantrasiddhi.presentation.feedback.FeedbackStringProvider
import com.mantrasiddhi.presentation.theme.ScoreBands
import com.mantrasiddhi.presentation.theme.ScoreLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for the Recitation Screen.
 */
sealed interface RecitationUiState {
    data object Loading : RecitationUiState
    
    data class Active(
        val user: UserProfile,
        val activeTarget: Target?,
        val isListening: Boolean,
        val currentSpeechText: String,
        val recentSessions: List<RecitationSession>,
        val lastFeedback: RecitationFeedbackState? = null,
        val showCelebration: Boolean = false
    ) : RecitationUiState
    
    data class Error(val message: String) : RecitationUiState
}

/**
 * State representing feedback for the latest recitation.
 *
 * @property lowConfidence True when the audio was too unclear to score reliably;
 *   the UI should prompt the user to retry rather than show a low score.
 */
data class RecitationFeedbackState(
    val isCorrect: Boolean,
    val overallScore: Float,
    val worstWord: String?,
    val suggestion: String?,
    val lowConfidence: Boolean = false
)

/**
 * One-shot UI events emitted from the Recitation screen (e.g. for snackbars).
 */
sealed interface RecitationEvent {
    data object TargetCreated : RecitationEvent
    data class ShowError(val message: String) : RecitationEvent
}

private class BoundaryReachedException(val text: String) : Exception()
private class SpeechRecognitionException(val msg: String) : Exception()

/**
 * ViewModel orchestrating continuous recitation counting in Recitation Mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecitationViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getActiveTargetUseCase: GetActiveTargetUseCase,
    private val createTargetUseCase: CreateTargetUseCase,
    private val getSessionsForUserUseCase: GetSessionsForUserUseCase,
    private val speechRecognitionService: SpeechRecognitionService,
    private val evaluateRecitationUseCase: EvaluateRecitationUseCase,
    private val detectRecitationBoundaryUseCase: DetectRecitationBoundaryUseCase,
    private val soundEffectsPlayer: SoundEffectsPlayer,
    private val feedbackStringProvider: FeedbackStringProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecitationUiState>(RecitationUiState.Loading)
    val uiState: StateFlow<RecitationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecitationEvent>()
    val events: SharedFlow<RecitationEvent> = _events.asSharedFlow()

    private var activeUser: UserProfile? = null
    private var activeTarget: Target? = null
    private var recentSessionsList = emptyList<RecitationSession>()

    private var isListeningLoopActive = false
    private var listeningJob: Job? = null

    init {
        observeUserAndData()
    }

    private fun observeUserAndData() {
        viewModelScope.launch {
            getCurrentUserUseCase().flatMapLatest { user ->
                if (user != null) {
                    activeUser = user
                    activeTarget = getActiveTargetUseCase(user.id)
                    getSessionsForUserUseCase(user.id).catch { emit(emptyList()) }
                } else {
                    flowOf(null)
                }
            }.collect { sessions ->
                if (activeUser != null) {
                    if (sessions != null) {
                        recentSessionsList = sessions.take(5)
                    }
                    emitActiveState()
                } else {
                    _uiState.value = RecitationUiState.Error("No active user profile found.")
                }
            }
        }
    }

    private fun emitActiveState(
        isListening: Boolean = isListeningLoopActive,
        currentSpeechText: String = "",
        lastFeedback: RecitationFeedbackState? = null,
        showCelebration: Boolean = false
    ) {
        val user = activeUser ?: return
        viewModelScope.launch {
            val currentTarget = getActiveTargetUseCase(user.id)
            val oldTarget = activeTarget
            activeTarget = currentTarget

            val triggerCelebration = showCelebration || 
                (currentTarget != null && currentTarget.isCompleted && (oldTarget == null || !oldTarget.isCompleted))

            _uiState.value = RecitationUiState.Active(
                user = user,
                activeTarget = currentTarget,
                isListening = isListening,
                currentSpeechText = currentSpeechText,
                recentSessions = recentSessionsList,
                lastFeedback = lastFeedback,
                showCelebration = triggerCelebration
            )
        }
    }

    /**
     * Creates a recitation target for the current user (self-target).
     *
     * A money reward can only be attached by a parent profile; for any other
     * profile the amount is forced to zero so a child can never fund their own
     * reward. Only a parent may set a money target for a child.
     */
    fun createTarget(targetRecitationCount: Int, moneyAmount: Double) {
        val user = activeUser
        if (user == null) {
            viewModelScope.launch {
                _events.emit(RecitationEvent.ShowError("No active user profile found."))
            }
            return
        }
        val safeMoney = if (user.profileType == ProfileType.PARENT) moneyAmount else 0.0
        viewModelScope.launch {
            val target = Target(
                userId = user.id,
                setByUserId = user.id,
                targetRecitationCount = targetRecitationCount,
                currentRecitationCount = 0,
                moneyAmount = safeMoney,
                isCompleted = false
            )
            createTargetUseCase(target).onSuccess {
                emitActiveState()
                _events.emit(RecitationEvent.TargetCreated)
            }.onFailure { e ->
                _events.emit(RecitationEvent.ShowError(e.message ?: "Failed to set target."))
            }
        }
    }

    /**
     * Starts the continuous recitation listening loop.
     */
    fun startRecitation() {
        if (isListeningLoopActive) return
        soundEffectsPlayer.triggerLightHaptic()
        isListeningLoopActive = true
        runListeningLoop()
    }

    /**
     * Pauses the continuous recitation listening loop.
     */
    fun pauseRecitation() {
        soundEffectsPlayer.triggerLightHaptic()
        isListeningLoopActive = false
        listeningJob?.cancel()
        listeningJob = null
        emitActiveState(isListening = false)
    }

    /**
     * Stops the continuous recitation listening loop and clears feedback overlays.
     */
    fun stopRecitation() {
        soundEffectsPlayer.triggerLightHaptic()
        isListeningLoopActive = false
        listeningJob?.cancel()
        listeningJob = null
        emitActiveState(isListening = false, lastFeedback = null)
    }

    /**
     * Dismisses the celebration overlay.
     */
    fun dismissCelebration() {
        val currentState = _uiState.value as? RecitationUiState.Active ?: return
        _uiState.value = currentState.copy(showCelebration = false)
    }

    private fun runListeningLoop() {
        listeningJob?.cancel()
        listeningJob = viewModelScope.launch {
            while (isListeningLoopActive) {
                emitActiveState(isListening = true, currentSpeechText = "")
                var accumulatedText = ""
                var lastSpeechTimeMs = System.currentTimeMillis()
                var boundaryReachedText: String? = null
                var finalAlternatives: List<Pair<String, Float>> = emptyList()
                var finalConfidence = 1.0f
                val loopStartTimeMs = System.currentTimeMillis()

                try {
                    kotlinx.coroutines.coroutineScope {
                        val parentScope = this
                        
                        launch {
                            try {
                                speechRecognitionService.startListening()
                                    .flowOn(Dispatchers.Main)
                                    .collect { result ->
                                        when (result) {
                                            is SpeechResult.Partial -> {
                                                accumulatedText = result.text
                                                lastSpeechTimeMs = System.currentTimeMillis()
                                                emitActiveState(isListening = true, currentSpeechText = accumulatedText)

                                                if (detectRecitationBoundaryUseCase(accumulatedText, 0L)) {
                                                    boundaryReachedText = accumulatedText
                                                    parentScope.cancel()
                                                }
                                            }
                                            is SpeechResult.Final -> {
                                                accumulatedText = result.text
                                                boundaryReachedText = accumulatedText
                                                finalAlternatives = result.alternatives
                                                finalConfidence = result.confidence
                                                parentScope.cancel()
                                            }
                                            is SpeechResult.Error -> {
                                                if (result.code == 8 /* SpeechRecognizer.ERROR_RECOGNIZER_BUSY */) {
                                                    delay(500)
                                                }
                                                if (accumulatedText.isNotBlank()) {
                                                    boundaryReachedText = accumulatedText
                                                }
                                                parentScope.cancel()
                                            }
                                        }
                                    }
                            } catch (e: Exception) {
                                // Cancel parent scope on speech collection errors
                                parentScope.cancel()
                            }
                        }

                        launch {
                            while (isActive && isListeningLoopActive) {
                                delay(200)
                                val elapsed = System.currentTimeMillis() - lastSpeechTimeMs
                                if (elapsed >= 2000L && accumulatedText.isNotBlank()) {
                                    if (detectRecitationBoundaryUseCase(accumulatedText, elapsed)) {
                                        boundaryReachedText = accumulatedText
                                        parentScope.cancel()
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // coroutineScope cancelled because boundary was detected or error occurred
                }

                val finalOutputText = boundaryReachedText
                if (finalOutputText != null && finalOutputText.isNotBlank()) {
                    val duration = System.currentTimeMillis() - loopStartTimeMs
                    evaluateAndProcess(finalOutputText, duration, finalAlternatives, finalConfidence)
                    delay(2000)
                    emitActiveState(isListening = isListeningLoopActive, currentSpeechText = "", lastFeedback = null)
                } else {
                    delay(500)
                }
            }
        }
    }

    private suspend fun evaluateAndProcess(
        transcription: String,
        durationMs: Long,
        alternatives: List<Pair<String, Float>> = emptyList(),
        confidence: Float = 1.0f
    ) {
        val user = activeUser ?: return
        try {
            val evaluation = evaluateRecitationUseCase(
                userId = user.id,
                transcription = transcription,
                recitationTimeMs = durationMs,
                mode = RecitationMode.NORMAL,
                alternatives = alternatives,
                confidence = confidence
            )

            var worstWord: String? = null
            var suggestion: String? = null
            if (evaluation.lowConfidence) {
                // Don't punish pronunciation for audio we couldn't hear clearly.
                suggestion = feedbackStringProvider.unclearAudioMessage()
            } else if (!evaluation.isCorrect) {
                val worstResult = evaluation.wordResults
                    .filter { ScoreBands.levelFor(it.score) != ScoreLevel.STRONG }
                    .minByOrNull { it.score }
                worstWord = worstResult?.referenceWord
                if (worstResult != null) {
                    suggestion = feedbackStringProvider.suggestionFor(worstResult.referenceWord, worstResult.spokenWord)
                }
            }

            val feedback = RecitationFeedbackState(
                isCorrect = evaluation.isCorrect,
                overallScore = evaluation.overallScore,
                worstWord = worstWord,
                suggestion = suggestion,
                lowConfidence = evaluation.lowConfidence
            )

            val oldTarget = activeTarget
            val newTarget = getActiveTargetUseCase(user.id)
            activeTarget = newTarget

            val triggerCelebration = newTarget != null && newTarget.isCompleted && (oldTarget == null || !oldTarget.isCompleted)

            if (triggerCelebration) {
                soundEffectsPlayer.playAchievementSound()
            } else if (evaluation.isCorrect) {
                soundEffectsPlayer.playCorrectSound()
            }

            emitActiveState(
                isListening = isListeningLoopActive,
                currentSpeechText = transcription,
                lastFeedback = feedback,
                showCelebration = triggerCelebration
            )
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onCleared() {
        super.onCleared()
        listeningJob?.cancel()
    }
}
