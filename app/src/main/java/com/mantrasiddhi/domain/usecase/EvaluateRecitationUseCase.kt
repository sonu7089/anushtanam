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
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.AppPreferencesRepository
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case to evaluate a Gayatri Mantra recitation, save the session,
 * and increment active target count if correct and in normal mode.
 */
class EvaluateRecitationUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository,
    private val incrementTargetUseCase: IncrementTargetUseCase,
    private val appPreferencesRepository: AppPreferencesRepository
) {

    /**
     * Evaluates a completed recitation, saves its session data, and updates target progress.
     *
     * The user's recitation-sensitivity preference is plumbed into the evaluation
     * pass threshold, so the Settings slider measurably shifts the pass/fail boundary.
     * When [alternatives] are supplied, the best-aligning hypothesis is chosen so the
     * correct words are recovered even if they sit in a lower-ranked STT result.
     *
     * @param userId The ID of the user performing the recitation.
     * @param transcription The 1-best transcription string from speech-to-text.
     * @param recitationTimeMs The duration of the recitation in milliseconds.
     * @param mode The recitation mode (PRACTICE or NORMAL).
     * @param alternatives The full n-best hypotheses (transcription to confidence),
     *   best-first. Defaults to just [transcription] when n-best is unavailable.
     * @param confidence STT confidence of [transcription] in `0.0..1.0`.
     * @return The evaluation summary detailing scores and word alignment.
     */
    suspend operator fun invoke(
        userId: Long,
        transcription: String,
        recitationTimeMs: Long,
        mode: RecitationMode,
        alternatives: List<Pair<String, Float>> = emptyList(),
        confidence: Float = 1.0f
    ): MantraEvaluation {
        val sensitivity = appPreferencesRepository.recitationSensitivity.first()
        val config = EvaluationConfig.DEFAULT.copy(passThreshold = sensitivity.toFloat())
        val hypotheses = alternatives.ifEmpty { listOf(transcription to confidence) }
        val evaluation = MantraEvaluator.evaluateBest(hypotheses, recitationTimeMs, config)
        val wordScores = evaluation.wordResults.associate {
            it.referenceWord to it.score.toDouble()
        }
        val session = RecitationSession(
            userId = userId,
            mode = mode,
            overallScore = evaluation.overallScore.toDouble(),
            wordScores = wordScores,
            transcription = transcription,
            isCorrect = evaluation.isCorrect,
            durationMs = recitationTimeMs
        )
        recitationRepository.saveSession(session)

        if (mode == RecitationMode.NORMAL && evaluation.isCorrect) {
            incrementTargetUseCase(userId)
        }
        return evaluation
    }
}
