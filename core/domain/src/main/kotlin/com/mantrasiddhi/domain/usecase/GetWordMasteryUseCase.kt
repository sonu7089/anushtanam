/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.mantra.ActiveMantra
import com.mantrasiddhi.domain.mantra.Mantra
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Domain model representing mastery score of a single word.
 *
 * @property word The reference word (transliteration).
 * @property averageScore Mean per-word score across the user's sessions (`0.0..1.0`).
 * @property totalAttempts Number of sessions that contributed a score for this word.
 * @property pada The 1-based pada (line) this word belongs to.
 */
data class WordMastery(
    val word: String,
    val averageScore: Double,
    val totalAttempts: Int,
    val pada: Int = 0
)

/**
 * Mastery of a whole pada (line), aggregated from its words (§4.4 per-pada trends).
 *
 * @property pada The 1-based pada number.
 * @property averageScore Mean per-word score across the words of this pada.
 * @property totalAttempts Total per-word attempts contributing to this pada.
 */
data class PadaMastery(
    val pada: Int,
    val averageScore: Double,
    val totalAttempts: Int
)

/**
 * Computes per-word (and per-pada) average scores across a user's sessions.
 *
 * Only sessions of the active mantra count. Word scores are keyed by transliteration,
 * which means nothing outside the mantra it came from, so mixing mantras would drag every
 * average towards zero as soon as the app carried a second one.
 */
class GetWordMasteryUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository,
    @ActiveMantra private val mantra: Mantra
) {
    /** Per-word mastery across all of the user's sessions, in mantra order. */
    operator fun invoke(userId: Long): Flow<List<WordMastery>> =
        sessions(userId).map(::wordMastery)

    /** Per-pada mastery, aggregating the per-word scores of each line. */
    fun padaMastery(userId: Long): Flow<List<PadaMastery>> = sessions(userId).map { sessions ->
        val byWord = wordMastery(sessions)
        (1..mantra.padaCount).map { pada ->
            val padaWords = byWord.filter { it.pada == pada }
            PadaMastery(
                pada = pada,
                averageScore = if (padaWords.isEmpty()) 0.0 else padaWords.sumOf { it.averageScore } / padaWords.size,
                totalAttempts = padaWords.sumOf { it.totalAttempts }
            )
        }
    }

    private fun sessions(userId: Long): Flow<List<RecitationSession>> =
        recitationRepository.getSessionsForUser(userId)
            .map { sessions -> sessions.filter { it.mantraId == mantra.id } }

    private fun wordMastery(sessions: List<RecitationSession>): List<WordMastery> =
        mantra.words.map { word ->
            val transliteration = word.transliteration
            val sum = sessions.sumOf { it.wordScores[transliteration] ?: 0.0 }
            WordMastery(
                word = transliteration,
                averageScore = if (sessions.isEmpty()) 0.0 else sum / sessions.size,
                totalAttempts = sessions.size,
                pada = word.pada
            )
        }
}
