/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GetPracticePlanUseCaseTest {

    private val now = 1_000_000_000_000L
    private val dayMs = 24 * 60 * 60 * 1000L

    private fun session(
        wordScores: Map<String, Double>,
        createdAt: Long
    ): RecitationSession = RecitationSession(
        userId = 1L,
        mode = RecitationMode.PRACTICE,
        overallScore = wordScores.values.average(),
        wordScores = wordScores,
        transcription = "",
        isCorrect = false,
        durationMs = 5000L,
        createdAt = createdAt
    )

    /** Every reference word scored [mastered], overriding the given weak words. */
    private fun scores(weak: Map<String, Double>, mastered: Double = 0.95): Map<String, Double> =
        GayatriMantra.transliterations.associateWith { weak[it] ?: mastered }

    @Test
    fun should_surfaceWeakestWordsFirst() {
        val sessions = listOf(
            session(scores(mapOf("varenyam" to 0.40, "dhimahi" to 0.55)), now),
            session(scores(mapOf("varenyam" to 0.45, "dhimahi" to 0.60)), now)
        )

        val plan = GetPracticePlanUseCase.plan(sessions, now, limit = 3)

        assertTrue(plan.items.isNotEmpty())
        assertEquals("varenyam", plan.items.first().word)
        assertTrue(plan.items.any { it.word == "dhimahi" })
        // Mastered, freshly-practised words should not crowd the drill.
        assertTrue(plan.items.none { it.word == "om" })
    }

    @Test
    fun should_resurfaceOverdueMasteredWord() {
        // All words mastered, but last practised long ago -> spaced repetition resurfaces them.
        val sessions = listOf(session(scores(emptyMap()), now - 30 * dayMs))

        val plan = GetPracticePlanUseCase.plan(sessions, now, limit = 3)

        assertTrue(plan.items.isNotEmpty(), "overdue mastered words should resurface")
    }

    @Test
    fun should_recommendNewWords_when_noSessions() {
        val plan = GetPracticePlanUseCase.plan(emptyList(), now)
        assertTrue(plan.items.isNotEmpty())
        assertTrue(plan.items.all { it.reason == PracticeReason.NEW })
    }
}
