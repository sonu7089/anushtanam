/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.mantra.ActiveMantra
import com.mantrasiddhi.domain.mantra.Mantra
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Finds the words a child struggles with most: the lowest mean per-word score across
 * their sessions of the active mantra.
 *
 * A word the child has never been scored on counts as `0.0`, which is deliberate — an
 * untouched word is exactly what a parent should see surfaced first.
 */
class GetChildWeakWordsUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository,
    @ActiveMantra private val mantra: Mantra
) {
    /**
     * @param childId The learner whose sessions are analysed.
     * @param limit How many words to surface, weakest first.
     */
    operator fun invoke(childId: Long, limit: Int = DEFAULT_LIMIT): Flow<List<String>> =
        recitationRepository.getSessionsForUser(childId).map { allSessions ->
            val sessions = allSessions.filter { it.mantraId == mantra.id }
            if (sessions.isEmpty()) {
                return@map emptyList()
            }
            mantra.transliterations
                .map { word -> word to sessions.sumOf { it.wordScores[word] ?: 0.0 } / sessions.size }
                .sortedBy { (_, meanScore) -> meanScore }
                .take(limit)
                .map { (word, _) -> word }
        }

    private companion object {
        const val DEFAULT_LIMIT = 3
    }
}
