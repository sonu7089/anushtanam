/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.evaluation.EvaluationConfig
import com.mantrasiddhi.domain.evaluation.MantraEvaluation
import com.mantrasiddhi.domain.evaluation.MantraEvaluator
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAnalyzer
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAssessment
import com.mantrasiddhi.domain.evaluation.acoustic.RecordedAudio
import com.mantrasiddhi.domain.evaluation.acoustic.SwaraAnalyzer
import com.mantrasiddhi.domain.evaluation.acoustic.SwaraAssessment
import com.mantrasiddhi.domain.evaluation.diagnostics.DiagnosticEvent
import com.mantrasiddhi.domain.evaluation.diagnostics.EvaluationDiagnostics
import com.mantrasiddhi.domain.evaluation.model.RecitationAssessment
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.AppPreferencesRepository
import com.mantrasiddhi.domain.repository.ProgressSyncRepository
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case to evaluate a Gayatri Mantra recitation, save the session,
 * and increment active target count if correct and in normal mode.
 *
 * The same verified-japa branch is the only thing that advances an active
 * Purashcharana, so that long sankalpa can only ever be filled with japa the app
 * itself heard and accepted.
 */
class EvaluateRecitationUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository,
    private val incrementTargetUseCase: IncrementTargetUseCase,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val acousticAnalyzer: AcousticAnalyzer,
    private val determineLearnerLevelUseCase: DetermineLearnerLevelUseCase,
    private val evaluationDiagnostics: EvaluationDiagnostics,
    private val swaraAnalyzer: SwaraAnalyzer,
    private val progressSyncRepository: ProgressSyncRepository,
    private val incrementPurashcharanaUseCase: IncrementPurashcharanaUseCase
) {

    /**
     * Evaluates a completed recitation, saves its session data, and updates target progress.
     *
     * The evaluation pass gate is resolved per §4.3: an explicit
     * recitation-sensitivity preference always wins; otherwise the gate auto-adapts to
     * the learner's [com.mantrasiddhi.domain.model.LearnerLevel] (lenient for
     * beginners, strict for advanced). When [alternatives] are supplied, the
     * best-aligning hypothesis is chosen so the correct words are recovered even if
     * they sit in a lower-ranked STT result.
     *
     * When [audio] is supplied, the Phase-3 acoustic layer measures pronunciation
     * directly from the waveform (DTW vs the benchmark) and fuses it into the
     * pronunciation axis; without audio the engine degrades to the text-only estimate.
     *
     * @param userId The ID of the user performing the recitation.
     * @param transcription The 1-best transcription string from speech-to-text.
     * @param recitationTimeMs The duration of the recitation in milliseconds.
     * @param mode The recitation mode (PRACTICE or NORMAL).
     * @param alternatives The full n-best hypotheses (transcription to confidence),
     *   best-first. Defaults to just [transcription] when n-best is unavailable.
     * @param confidence STT confidence of [transcription] in `0.0..1.0`.
     * @param audio Optional captured recitation audio for acoustic pronunciation
     *   scoring; `null` uses the text-only path.
     * @return The evaluation summary detailing scores and word alignment.
     */
    suspend operator fun invoke(
        userId: Long,
        transcription: String,
        recitationTimeMs: Long,
        mode: RecitationMode,
        alternatives: List<Pair<String, Float>> = emptyList(),
        confidence: Float = 1.0f,
        audio: RecordedAudio? = null
    ): MantraEvaluation {
        val config = resolveConfig(userId)
        val hypotheses = alternatives.ifEmpty { listOf(transcription to confidence) }
        val acoustic = analyzeAudio(audio, hypotheses, config)
        val assessment = MantraEvaluator.assessBest(hypotheses, recitationTimeMs, config, acoustic)
        val swara = analyzeSwara(audio, config)
        val evaluation = MantraEvaluator.toEvaluation(assessment).copy(swara = swara)
        saveSession(userId, mode, transcription, recitationTimeMs, assessment)
        // Mirror the session into the cloud progress summary (all sessions, both
        // modes). Best-effort: a sync failure must never affect the evaluation.
        runCatching {
            progressSyncRepository.recordSession(
                localUserId = userId,
                overallScore = assessment.overall.score.toDouble(),
                isCorrect = evaluation.isCorrect
            )
        }
        recordDiagnostics(transcription, assessment, acoustic, audio)

        if (mode == RecitationMode.NORMAL && evaluation.isCorrect) {
            incrementTargetUseCase(userId)
            // Only japa Anushtanam actually heard and verified advances the
            // Purashcharana: a sankalpa of 24,00,000 japa means nothing if a count can
            // be typed in or earned in Abhyas, where the sadhak is still learning and
            // may stop mid-mantra. This one branch is the only place the count moves.
            // Best-effort: a Purashcharana failure must never break scoring, which is
            // what the sadhak is actually waiting for.
            runCatching { incrementPurashcharanaUseCase(userId) }
        }
        return evaluation
    }

    /**
     * Resolves the evaluation config: an explicit sensitivity preference overrides the
     * automatic learner-level adaptation (§4.3 explicit > auto).
     */
    private suspend fun resolveConfig(userId: Long): EvaluationConfig {
        val explicit = appPreferencesRepository.isRecitationSensitivityExplicit.first()
        return if (explicit) {
            val sensitivity = appPreferencesRepository.recitationSensitivity.first()
            EvaluationConfig.DEFAULT.copy(passThreshold = sensitivity.toFloat())
        } else {
            EvaluationConfig.DEFAULT.adaptedForLevel(determineLearnerLevelUseCase(userId))
        }
    }

    /** Builds and persists the session, capturing all three axes (§4.4). */
    private suspend fun saveSession(
        userId: Long,
        mode: RecitationMode,
        transcription: String,
        recitationTimeMs: Long,
        assessment: RecitationAssessment
    ) {
        val wordScores = assessment.wordResults.associate { it.referenceWord to it.score.toDouble() }
        val session = RecitationSession(
            userId = userId,
            mode = mode,
            overallScore = assessment.overall.score.toDouble(),
            wordScores = wordScores,
            transcription = transcription,
            isCorrect = assessment.isPass,
            durationMs = recitationTimeMs,
            correctness = assessment.correctness.score.toDouble(),
            completeness = assessment.completeness.score.toDouble(),
            pronunciation = assessment.pronunciation.score.toDouble()
        )
        recitationRepository.saveSession(session)
    }

    /**
     * Runs the acoustic analyzer for [audio], using the best hypothesis confidence
     * for the quality gate. Returns `null` (text-only path) when no audio was captured.
     */
    private suspend fun analyzeAudio(
        audio: RecordedAudio?,
        hypotheses: List<Pair<String, Float>>,
        config: EvaluationConfig
    ): AcousticAssessment? {
        if (audio == null || audio.isEmpty) return null
        val confidence = hypotheses.firstOrNull()?.second ?: 1.0f
        return acousticAnalyzer.analyze(audio, confidence, config)
    }

    /**
     * Runs the optional swara/pitch layer (§5.5) only when the user has enabled
     * Advanced/Vedic mode and audio was captured. Returns `null` otherwise so default
     * scoring is entirely unaffected when the feature is off.
     */
    private suspend fun analyzeSwara(audio: RecordedAudio?, config: EvaluationConfig): SwaraAssessment? {
        if (audio == null || audio.isEmpty) return null
        if (!appPreferencesRepository.advancedVedicMode.first()) return null
        return swaraAnalyzer.analyze(audio, config)
    }

    /**
     * Records on-device diagnostics (§5.4) for recitations the engine could not score
     * with full confidence — low STT confidence, or captured audio that was dropped /
     * too noisy to assess acoustically. These flag real-world captures worth adding to
     * the calibration corpus. Clean, fully-assessed recitations are not recorded to
     * keep the on-device log focused and small.
     */
    private suspend fun recordDiagnostics(
        transcription: String,
        assessment: RecitationAssessment,
        acoustic: AcousticAssessment?,
        audio: RecordedAudio?
    ) {
        val audioCaptured = audio != null && !audio.isEmpty
        val degraded = assessment.lowConfidence || (audioCaptured && !assessment.acousticAssessed)
        if (!degraded) return
        evaluationDiagnostics.record(
            DiagnosticEvent(
                transcription = transcription,
                overall = assessment.overall.score,
                correctness = assessment.correctness.score,
                completeness = assessment.completeness.score,
                pronunciation = assessment.pronunciation.score,
                lowConfidence = assessment.lowConfidence,
                audioCaptured = audioCaptured,
                acousticAssessed = assessment.acousticAssessed,
                snrDb = acoustic?.snrDb,
                timestampMs = System.currentTimeMillis()
            )
        )
    }
}
