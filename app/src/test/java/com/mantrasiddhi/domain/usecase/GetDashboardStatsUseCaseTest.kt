/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.model.Savings
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.repository.RecitationRepository
import com.mantrasiddhi.domain.repository.SavingsRepository
import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Calendar

class GetDashboardStatsUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var targetRepository: TargetRepository
    private lateinit var savingsRepository: SavingsRepository
    private lateinit var recitationRepository: RecitationRepository
    private lateinit var getChildProgressUseCase: GetChildProgressUseCase
    private lateinit var getDashboardStatsUseCase: GetDashboardStatsUseCase

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        targetRepository = mockk()
        savingsRepository = mockk()
        recitationRepository = mockk()
        getChildProgressUseCase = mockk()
        getDashboardStatsUseCase = GetDashboardStatsUseCase(
            userRepository,
            targetRepository,
            savingsRepository,
            recitationRepository,
            getChildProgressUseCase
        )
    }

    @Test
    fun `should_aggregateStatsCorrectly_forIndividualUser`() = runTest {
        // Given
        val userId = 1L
        val user = UserProfile(id = userId, name = "John", profileType = ProfileType.INDIVIDUAL, avatarIndex = 0)
        val target = Target(id = 10L, userId = userId, setByUserId = userId, targetRecitationCount = 10, currentRecitationCount = 5, isCompleted = false)
        val savings = Savings(userId = userId, totalLocked = 100.0, totalSaved = 50.0, totalUnlocked = 50.0)
        
        val today = Calendar.getInstance().timeInMillis
        val session1 = RecitationSession(
            id = 1,
            userId = userId,
            mode = RecitationMode.NORMAL,
            overallScore = 1.0,
            wordScores = emptyMap(),
            transcription = "",
            isCorrect = true,
            durationMs = 1000L,
            createdAt = today
        )
        val session2 = RecitationSession(
            id = 2,
            userId = userId,
            mode = RecitationMode.NORMAL,
            overallScore = 1.0,
            wordScores = emptyMap(),
            transcription = "",
            isCorrect = true,
            durationMs = 1000L,
            createdAt = today
        )
        
        coEvery { userRepository.getById(userId) } returns user
        every { targetRepository.getAllForUser(userId) } returns flowOf(listOf(target))
        every { savingsRepository.getSavingsFlowForUser(userId) } returns flowOf(savings)
        every { recitationRepository.getSessionsForUser(userId) } returns flowOf(listOf(session1, session2))
        every { userRepository.getChildrenOf(userId) } returns flowOf(emptyList())

        // When
        val stats = getDashboardStatsUseCase(userId).first()

        // Then
        assertNotNull(stats)
        assertEquals(user, stats?.user)
        assertEquals(target, stats?.activeTarget)
        assertEquals(savings, stats?.savings)
        assertEquals(2, stats?.todayRecitationCount)
        assertEquals(1, stats?.streakDays)
        assertTrue(stats?.childrenProgress?.isEmpty() == true)
    }
}
