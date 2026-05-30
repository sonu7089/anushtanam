/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Domain model representing mastery score of a single word.
 */
data class WordMastery(
    val word: String,
    val averageScore: Double,
    val totalAttempts: Int
)

/**
 * UseCase to compute per-word average scores across all sessions of a user.
 */
class GetWordMasteryUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository
) {
    operator fun invoke(userId: Long): Flow<List<WordMastery>> {
        return recitationRepository.getSessionsForUser(userId).map { sessions ->
            if (sessions.isEmpty()) {
                GayatriMantra.transliterations.map { word ->
                    WordMastery(word = word, averageScore = 0.0, totalAttempts = 0)
                }
            } else {
                val wordScoresSum = mutableMapOf<String, Double>()
                val wordAttemptsCount = mutableMapOf<String, Int>()

                for (session in sessions) {
                    for (word in GayatriMantra.transliterations) {
                        val score = session.wordScores[word] ?: 0.0
                        wordScoresSum[word] = (wordScoresSum[word] ?: 0.0) + score
                        wordAttemptsCount[word] = (wordAttemptsCount[word] ?: 0) + 1
                    }
                }

                GayatriMantra.transliterations.map { word ->
                    val sum = wordScoresSum[word] ?: 0.0
                    val count = wordAttemptsCount[word] ?: 0
                    WordMastery(
                        word = word,
                        averageScore = if (count > 0) sum / count else 0.0,
                        totalAttempts = count
                    )
                }
            }
        }
    }
}
