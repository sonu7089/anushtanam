/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.evaluation.MantraEvaluation
import com.mantrasiddhi.domain.evaluation.MantraEvaluator
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.RecitationRepository
import javax.inject.Inject

/**
 * Use case to evaluate a Gayatri Mantra recitation, save the session,
 * and increment active target count if correct and in normal mode.
 */
class EvaluateRecitationUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository,
    private val incrementTargetUseCase: IncrementTargetUseCase
) {

    /**
     * Evaluates a completed recitation, saves its session data, and updates target progress.
     *
     * @param userId The ID of the user performing the recitation.
     * @param transcription The transcription string from speech-to-text.
     * @param recitationTimeMs The duration of the recitation in milliseconds.
     * @param mode The recitation mode (PRACTICE or NORMAL).
     * @return The evaluation summary detailing scores and word alignment.
     */
    suspend operator fun invoke(
        userId: Long,
        transcription: String,
        recitationTimeMs: Long,
        mode: RecitationMode
    ): MantraEvaluation {
        val evaluation = MantraEvaluator.evaluate(transcription, recitationTimeMs)
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
