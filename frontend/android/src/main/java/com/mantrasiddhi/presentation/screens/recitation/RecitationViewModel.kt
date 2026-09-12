/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.recitation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.data.speech.SpeechRecognitionService
import com.mantrasiddhi.data.speech.RecitationClock
import com.mantrasiddhi.data.speech.SpeechResult
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.CreateTargetUseCase
import com.mantrasiddhi.domain.usecase.DetectRecitationBoundaryUseCase
import com.mantrasiddhi.domain.usecase.EvaluateRecitationUseCase
import com.mantrasiddhi.domain.usecase.FlushPurashcharanaSyncUseCase
import com.mantrasiddhi.domain.usecase.GetActiveTargetUseCase
import com.mantrasiddhi.data.audio.SoundEffectsPlayer
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.GetSessionsForUserUseCase
import com.mantrasiddhi.domain.usecase.IncrementActiveCloudGoalUseCase
import com.mantrasiddhi.presentation.feedback.FeedbackStringProvider
import com.mantrasiddhi.presentation.theme.ScoreBands
import com.mantrasiddhi.presentation.theme.ScoreLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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
        val showCelebration: Boolean = false,
        // The just-completed target whose reward the celebration overlay displays.
        // Held separately because a completed target is no longer the "active" target
        // (getActiveForUser excludes completed rows), so activeTarget is null by then.
        val celebrationTarget: Target? = null,
        // Monotonically increasing id, bumped exactly once per counted japa, so the UI can
        // key a LaunchedEffect on it and fire one confirmation pulse. It is an id and not a
        // boolean because this state is re-emitted on every partial speech result: a flag
        // would either re-trigger on every emission or be cleared before it could be seen.
        // 0 means "no japa counted in this session yet".
        val japaCountedPulseId: Long = 0L
    ) : RecitationUiState

    /**
     * A resource id rather than a resolved string: the ViewModel has no Context and must
     * not bake English into state. The composable resolves it with `stringResource`.
     */
    data class Error(@StringRes val messageResId: Int) : RecitationUiState
}

/**
 * State representing feedback for the latest recitation.
 *
 * Only emitted for attempts that need the reciter's attention (incorrect / retry);
 * a correct recitation is acknowledged by the progress update and the
 * expanding-ring pulse ([RecitationUiState.Active.japaCountedPulseId]) — never by a card
 * the sadhak has to read or dismiss mid-japa.
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

    /**
     * @param messageResId Localized reason, resolved by the composable.
     * @param detail A throwable's own message when one exists. It is not localizable, so it
     *   is kept separate from [messageResId] instead of being formatted into state here.
     */
    data class ShowError(
        @StringRes val messageResId: Int,
        val detail: String? = null
    ) : RecitationEvent
}

/**
 * ViewModel orchestrating continuous recitation counting in Recitation Mode.
 *
 * ## Core listening design (rewritten)
 *
 * One recognizer session stays open while the reciter chants. The session transcript
 * grows through partial results; every time the mantra's closing word
 * ("प्रचोदयात्" / "prachodayat") appears, the text since the previous cut is *segmented
 * out* and queued for evaluation WITHOUT tearing the session down. This is what makes
 * back-to-back recitation work: reciting mantra #2 half a second after mantra #1 keeps
 * a single session alive, and each mantra is still counted individually.
 *
 * Evaluation runs on a separate sequential worker (a [Channel] consumer), so scoring
 * and DB writes never block listening. A silence watchdog flushes an incomplete tail
 * (an abandoned/failed attempt) after [DetectRecitationBoundaryUseCase.SILENCE_BOUNDARY_MS]
 * of quiet — also without ending the session. When the recognizer finalises on its own
 * (long silence) the loop restarts it after a very short delay.
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
    private val feedbackStringProvider: FeedbackStringProvider,
    private val incrementActiveCloudGoalUseCase: IncrementActiveCloudGoalUseCase,
    private val flushPurashcharanaSyncUseCase: FlushPurashcharanaSyncUseCase,
    private val clock: RecitationClock = RecitationClock(),
    private val teachingNotifications: com.mantrasiddhi.data.messages.TeachingNotifications? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecitationUiState>(RecitationUiState.Loading)
    val uiState: StateFlow<RecitationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecitationEvent>()
    val events: SharedFlow<RecitationEvent> = _events.asSharedFlow()

    private var activeUser: UserProfile? = null
    private var activeTarget: Target? = null
    private var recentSessionsList = emptyList<RecitationSession>()

    private var isListeningLoopActive = false
        set(value) {
            field = value
            teachingNotifications?.setPracticing(value)
        }
    private var listeningJob: Job? = null

    // --- Continuous-session segmentation state (all touched on the main dispatcher) ---

    /** Latest full transcript of the current recognizer session. */
    private var sessionText = ""

    /** How much of [sessionText] has already been dispatched for evaluation. */
    private var consumedChars = 0

    /** Monotonic start of the segment currently being recited (for duration). */
    private var segmentStartMs = 0L

    /** Monotonic time of the last changed non-blank speech result, for the silence flush. */
    private var lastSpeechTimeMs = 0L

    /**
     * Counter behind [RecitationUiState.Active.japaCountedPulseId]. Bumped only when an
     * evaluation actually counts, and then carried unchanged through every subsequent
     * emission until the next counted japa.
     */
    private var japaPulseCounter = 0L

    /** A cut mantra segment waiting to be scored. */
    private data class SegmentJob(
        val text: String,
        val alternatives: List<Pair<String, Float>>,
        val confidence: Float,
        val durationMs: Long
    )

    /**
     * Sequential evaluation queue. A single consumer scores segments in order, so two
     * back-to-back mantras can never race each other's target increments.
     */
    private val segmentQueue = Channel<SegmentJob>(Channel.UNLIMITED)

    init {
        observeUserAndData()
        viewModelScope.launch {
            for (job in segmentQueue) {
                evaluateAndProcess(job.text, job.durationMs, job.alternatives, job.confidence)
            }
        }
    }

    private fun observeUserAndData() {
        viewModelScope.launch {
            getCurrentUserUseCase().flatMapLatest { user ->
                activeUser = user
                if (user != null) {
                    activeTarget = getActiveTargetUseCase(user.id)
                    getSessionsForUserUseCase.recent(user.id, 5).catch { emit(emptyList()) }
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
                    _uiState.value =
                        RecitationUiState.Error(R.string.recitation_error_no_profile)
                }
            }
        }
    }

    /**
     * Publishes the Active state. By default the cached [activeTarget] is used so this
     * is cheap enough to call on every partial speech result; pass [refreshTarget] to
     * re-read the target from the DB (after an evaluation or target creation).
     *
     * [japaCountedPulseId] defaults to the current [japaPulseCounter] so every emission —
     * including the many triggered by partial speech results — carries the id of the last
     * counted japa unchanged. The UI therefore sees a *new* id only when one more japa was
     * actually counted, which is what makes a single confirmation pulse fire per mantra.
     */
    private fun emitActiveState(
        isListening: Boolean = isListeningLoopActive,
        currentSpeechText: String = (_uiState.value as? RecitationUiState.Active)?.currentSpeechText.orEmpty(),
        lastFeedback: RecitationFeedbackState? = (_uiState.value as? RecitationUiState.Active)?.lastFeedback,
        showCelebration: Boolean = (_uiState.value as? RecitationUiState.Active)?.showCelebration ?: false,
        celebrationTarget: Target? = (_uiState.value as? RecitationUiState.Active)?.celebrationTarget,
        japaCountedPulseId: Long = japaPulseCounter,
        refreshTarget: Boolean = false
    ) {
        val user = activeUser ?: return
        if (!refreshTarget) {
            _uiState.value = RecitationUiState.Active(
                user = user,
                activeTarget = activeTarget,
                isListening = isListening,
                currentSpeechText = currentSpeechText,
                recentSessions = recentSessionsList,
                lastFeedback = lastFeedback,
                showCelebration = showCelebration,
                celebrationTarget = celebrationTarget,
                japaCountedPulseId = japaCountedPulseId
            )
            return
        }
        emitActiveState(isListening, currentSpeechText, lastFeedback, showCelebration,
            celebrationTarget, japaCountedPulseId)
        viewModelScope.launch {
            activeTarget = getActiveTargetUseCase(user.id)
            // Refresh only the target; speech and feedback may have advanced during I/O.
            val current = _uiState.value as? RecitationUiState.Active ?: return@launch
            _uiState.value = current.copy(activeTarget = activeTarget)
        }
    }

    /**
     * Creates a recitation target for the current user (self-target).
     *
     * A reward can only be attached by a parent profile; for any other profile the
     * reward is forced to OTHER/blank so a child can never set their own reward.
     * This is a self-target (setByUserId == userId), so no parent-goal notification
     * is posted.
     */
    fun createTarget(targetRecitationCount: Int, rewardType: RewardType, rewardDescription: String) {
        val user = activeUser
        if (user == null) {
            viewModelScope.launch {
                _events.emit(RecitationEvent.ShowError(R.string.recitation_error_no_profile))
            }
            return
        }
        val isParent = user.profileType == ProfileType.PARENT
        val safeRewardType = if (isParent) rewardType else RewardType.OTHER
        val safeRewardDescription = if (isParent) rewardDescription else ""
        viewModelScope.launch {
            val target = Target(
                userId = user.id,
                setByUserId = user.id,
                targetRecitationCount = targetRecitationCount,
                currentRecitationCount = 0,
                rewardType = safeRewardType,
                rewardDescription = safeRewardDescription,
                isCompleted = false
            )
            createTargetUseCase(target).onSuccess {
                emitActiveState(refreshTarget = true)
                _events.emit(RecitationEvent.TargetCreated)
            }.onFailure { e ->
                _events.emit(
                    RecitationEvent.ShowError(
                        messageResId = R.string.recitation_error_target_failed,
                        detail = e.message
                    )
                )
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
     * Stops listening. Whatever was recited but not yet scored (the pending tail of
     * the transcript) is flushed to the evaluation queue FIRST, so tapping the mic
     * right after finishing a mantra still counts that mantra.
     */
    fun stopRecitation() {
        soundEffectsPlayer.triggerLightHaptic()
        if (isListeningLoopActive) {
            cutCompletedSegments()
            flushTail(emptyList(), 1.0f)
        }
        isListeningLoopActive = false
        listeningJob?.cancel()
        listeningJob = null
        flushPurashcharanaMirror()
        emitActiveState(isListening = false)
    }

    /**
     * Pushes the japa the Purashcharana cloud mirror is still holding back.
     *
     * The mirror throttles ordinary japa to protect the write budget, so the end of a
     * session is where the last stretch would otherwise sit only in Room until the next
     * recitation. Best-effort and fire-and-forget: nothing on this screen waits on it.
     */
    private fun flushPurashcharanaMirror() {
        val userId = activeUser?.id ?: return
        viewModelScope.launch { runCatching { flushPurashcharanaSyncUseCase(userId) } }
    }

    /**
     * Dismisses the celebration overlay.
     */
    fun dismissCelebration() {
        val currentState = _uiState.value as? RecitationUiState.Active ?: return
        _uiState.value = currentState.copy(showCelebration = false)
    }

    /** The not-yet-evaluated portion of the session transcript. */
    private fun tail(): String =
        sessionText.substring(consumedChars.coerceAtMost(sessionText.length)).trim()

    /**
     * Cuts every completed mantra out of the session transcript: each occurrence of
     * the closing word that lies beyond [consumedChars] ends one segment, which is
     * queued for evaluation. The recognizer session itself is left untouched, so the
     * next mantra can begin immediately — even mid-session with a sub-second pause.
     */
    private fun cutCompletedSegments() {
        val text = sessionText
        var cursor = consumedChars.coerceAtMost(text.length)
        for (match in DetectRecitationBoundaryUseCase.CLOSING_WORD_REGEX.findAll(text)) {
            val end = match.range.last + 1
            if (end <= cursor) continue // already part of an evaluated segment
            val segment = text.substring(cursor, end).trim()
            cursor = end
            if (segment.isNotBlank()) {
                enqueueSegment(segment, emptyList(), 1.0f)
            }
        }
        consumedChars = cursor
    }

    /**
     * Flushes the unfinished tail (an attempt that never reached the closing word) to
     * the evaluation queue. Used by the silence watchdog, session end, and manual stop.
     */
    private fun flushTail(alternatives: List<Pair<String, Float>>, confidence: Float) {
        val pending = tail()
        if (pending.isBlank()) {
            consumedChars = sessionText.length
            return
        }
        // n-best alternatives describe the WHOLE session transcript; only attach them
        // when this tail is the entire session (nothing was segmented out before it).
        val useAlternatives = if (consumedChars == 0) alternatives else emptyList()
        enqueueSegment(pending, useAlternatives, confidence)
        consumedChars = sessionText.length
    }

    private fun enqueueSegment(text: String, alternatives: List<Pair<String, Float>>, confidence: Float) {
        val now = clock.nowMs()
        val duration = (now - segmentStartMs).coerceAtLeast(0L)
        segmentStartMs = now
        segmentQueue.trySend(SegmentJob(text, alternatives, confidence, duration))
    }

    private fun runListeningLoop() {
        listeningJob?.cancel()
        listeningJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            while (isListeningLoopActive) {
                sessionText = ""
                consumedChars = 0
                segmentStartMs = clock.nowMs()
                lastSpeechTimeMs = clock.nowMs()
                var finalAlternatives: List<Pair<String, Float>> = emptyList()
                var finalConfidence = 1.0f
                var speechEndedAtMs: Long? = null
                val sessionStartedAtMs = clock.nowMs()
                emitActiveState(isListening = true, currentSpeechText = "")

                try {
                    coroutineScope {
                        val session = this

                        launch {
                            try {
                                speechRecognitionService.startListening()
                                    .flowOn(Dispatchers.Main)
                                    .collect { result ->
                                        when (result) {
                                            SpeechResult.EndOfSpeech -> if (speechEndedAtMs == null) { speechEndedAtMs = clock.nowMs() }
                                            is SpeechResult.Partial -> {
                                                if (result.text.isNotBlank() && result.text != sessionText) {
                                                    sessionText = result.text
                                                    lastSpeechTimeMs = clock.nowMs()
                                                    cutCompletedSegments()
                                                }
                                                emitActiveState(isListening = true, currentSpeechText = tail())
                                            }
                                            is SpeechResult.Final -> {
                                                if (result.text.isNotBlank()) {
                                                    sessionText = result.text
                                                }
                                                finalAlternatives = result.alternatives
                                                finalConfidence = result.confidence
                                                // Flow closes itself after Final; nothing to cancel here.
                                            }
                                            is SpeechResult.Error -> {
                                                if (result.code == 8 /* SpeechRecognizer.ERROR_RECOGNIZER_BUSY */) {
                                                    delay(500)
                                                }
                                                // Fatal/empty-input failures must be visible, not an endless restart.
                                                if (result.code !in setOf(6, 7, 8)) {
                                                    isListeningLoopActive = false
                                                    _events.emit(RecitationEvent.ShowError(result.messageResId))
                                                    emitActiveState(isListening = false)
                                                }
                                                // Flow closes itself after Error.
                                            }
                                        }
                                    }
                            } finally {
                                // Recognizer session ended (Final, Error, or teardown):
                                // stop the watchdog so the scope can complete.
                                session.cancel()
                            }
                        }

                        // Silence watchdog: flushes an abandoned/incomplete attempt after a
                        // long pause. Deliberately does NOT end the session — the reciter
                        // may simply be resting before the next round.
                        launch {
                            while (isActive) {
                                delay(200)
                                if (!isListeningLoopActive) {
                                    session.cancel()
                                    break
                                }
                                val silence = clock.nowMs() - lastSpeechTimeMs
                                val finalWaitExpired = speechEndedAtMs?.let { clock.nowMs() - it >= 1500L } == true
                                if (finalWaitExpired || silence >= 15000L || clock.nowMs() - sessionStartedAtMs >= 120000L) {
                                    session.cancel()
                                    break
                                }
                                if (silence >= DetectRecitationBoundaryUseCase.SILENCE_BOUNDARY_MS && tail().isNotBlank()) {
                                    flushTail(emptyList(), 1.0f)
                                    emitActiveState(isListening = true, currentSpeechText = "")
                                }
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    // Session scope wound down (recognizer closed, stop pressed, or VM cleared).
                } catch (_: Exception) {
                    isListeningLoopActive = false
                    _events.emit(RecitationEvent.ShowError(R.string.practice_error_speech_failed))
                    emitActiveState(isListening = false)
                }

                // Session over: segment anything the final transcript completed, then
                // flush the remainder (if any) with the recognizer's n-best hypotheses.
                cutCompletedSegments()
                flushTail(finalAlternatives, finalConfidence)

                if (!isListeningLoopActive) break
                // Tiny gap so the recognizer tears down cleanly before we reopen it.
                delay(SESSION_RESTART_DELAY_MS)
            }
        }
        listeningJob?.start()
    }

    private suspend fun evaluateAndProcess(
        transcription: String,
        durationMs: Long,
        alternatives: List<Pair<String, Float>> = emptyList(),
        confidence: Float = 1.0f
    ) {
        val user = activeUser ?: return
        try {
            // Snapshot the active target BEFORE evaluating: evaluateRecitationUseCase
            // increments (and may complete) the target internally. A completed target is
            // then excluded from getActiveForUser, so we must decide completion from this
            // pre-increment snapshot rather than re-fetching (which would return null).
            val targetBefore = getActiveTargetUseCase(user.id)

            val evaluation = evaluateRecitationUseCase(
                userId = user.id,
                transcription = transcription,
                recitationTimeMs = durationMs,
                mode = RecitationMode.NORMAL,
                alternatives = alternatives,
                confidence = confidence
            )

            // Saving the session also durably queues summary and assigned-goal accounting.

            // Silent visual confirmation. Bumping the id here — and only here — is what
            // makes JapaCountedPulse fire exactly one expanding ring per counted japa. The
            // target-completed path below falls through this same branch, so the pulse also
            // plays under the celebration dialog, which simply draws on top of it.
            if (evaluation.isCorrect) {
                japaPulseCounter++
            }

            // Correct recitations get no hint card — the ripple + progress update is the
            // feedback. Only an incorrect attempt surfaces a hint card.
            var feedback: RecitationFeedbackState? = null
            if (!evaluation.isCorrect) {
                var worstWord: String? = null
                var suggestion: String? = null
                if (!evaluation.lowConfidence) {
                    val worstResult = evaluation.wordResults
                        .filter { ScoreBands.levelFor(it.score) != ScoreLevel.STRONG }
                        .minByOrNull { it.score }
                    worstWord = worstResult?.referenceWord
                    if (worstResult != null) {
                        suggestion = feedbackStringProvider.suggestionFor(
                            worstResult.referenceWord,
                            worstResult.spokenWord
                        )
                    }
                }
                feedback = RecitationFeedbackState(
                    isCorrect = false,
                    overallScore = evaluation.overallScore,
                    worstWord = worstWord,
                    suggestion = suggestion,
                    lowConfidence = evaluation.lowConfidence
                )
            }

            // A correct recitation that pushes the pre-increment count to the goal marks
            // the local self-target complete. Mirrors IncrementTargetUseCase's own logic
            // (newCount = current + 1; completed when newCount >= target).
            val localTargetCompleted = targetBefore != null &&
                !targetBefore.isCompleted &&
                evaluation.isCorrect &&
                (targetBefore.currentRecitationCount + 1) >= targetBefore.targetRecitationCount
            val completedTarget = if (localTargetCompleted) {
                targetBefore!!.copy(
                    currentRecitationCount = targetBefore.currentRecitationCount + 1,
                    isCompleted = true
                )
            } else {
                null
            }

            // All correct-japa confirmation is silent, including target completion.

            // Reaching the local target ends the session: stop the listening loop so the
            // celebration overlay is not immediately cleared
            // by the loop reopening the mic. The reciter can start a fresh target after.
            if (localTargetCompleted) {
                isListeningLoopActive = false
                listeningJob?.cancel()
                listeningJob = null
            }

            emitActiveState(
                isListening = isListeningLoopActive,
                currentSpeechText = tail(),
                lastFeedback = feedback,
                showCelebration = localTargetCompleted,
                celebrationTarget = completedTarget,
                refreshTarget = true
            )

            // The queue drains after the loop has already been stopped, so this is where
            // the LAST japa of a session lands — including the one that completed a
            // target above. Flush here as well as in stopRecitation so the final japa is
            // mirrored even though it was counted after the mic was closed.
            if (!isListeningLoopActive) {
                flushPurashcharanaMirror()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            _events.emit(RecitationEvent.ShowError(R.string.practice_error_evaluate_failed))
        }
    }

    override fun onCleared() {
        teachingNotifications?.setPracticing(false)
        super.onCleared()
        listeningJob?.cancel()
        segmentQueue.close()
    }

    private companion object {
        /** Gap between recognizer sessions when the recognizer finalises on its own. */
        const val SESSION_RESTART_DELAY_MS = 150L
    }
}
