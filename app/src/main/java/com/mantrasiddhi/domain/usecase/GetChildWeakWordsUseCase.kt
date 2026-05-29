/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.evaluation.MantraEvaluator
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * UseCase to analyze a child's recitation sessions and find the top 3 words
 * with the lowest average score (i.e., where they struggle the most).
 */
class GetChildWeakWordsUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository
) {
    operator fun invoke(childId: Long): Flow<List<String>> {
        return recitationRepository.getSessionsForUser(childId).map { sessions ->
            if (sessions.isEmpty()) {
                return@map emptyList()
            }
            
            val wordSum = mutableMapOf<String, Double>()
            val wordCount = mutableMapOf<String, Int>()
            
            for (session in sessions) {
                for (word in MantraEvaluator.REFERENCE_WORDS) {
                    val score = session.wordScores[word] ?: 0.0
                    wordSum[word] = (wordSum[word] ?: 0.0) + score
                    wordCount[word] = (wordCount[word] ?: 0) + 1
                }
            }
            
            MantraEvaluator.REFERENCE_WORDS
                .map { word ->
                    val sum = wordSum[word] ?: 0.0
                    val count = wordCount[word] ?: 1
                    word to (sum / count)
                }
                .sortedBy { it.second }
                .take(3)
                .map { it.first }
        }
    }
}
