/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.repository.RecitationRepository
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class GetDashboardStatsUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var targetRepository: TargetRepository
    private lateinit var recitationRepository: RecitationRepository
    private lateinit var getChildProgressUseCase: GetChildProgressUseCase
    private lateinit var getDashboardStatsUseCase: GetDashboardStatsUseCase

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        targetRepository = mockk()
        recitationRepository = mockk()
        getChildProgressUseCase = mockk()
        getDashboardStatsUseCase = GetDashboardStatsUseCase(
            userRepository,
            targetRepository,
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

        val today = Calendar.getInstance().timeInMillis
        val session1 = RecitationSession(
            id = 1,
            userId = userId,
            mantraId = gayatri.id,
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
            mantraId = gayatri.id,
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
        every { recitationRepository.getSessionsForUser(userId) } returns flowOf(listOf(session1, session2))
        every { userRepository.getChildrenOf(userId) } returns flowOf(emptyList())

        // When
        val stats = getDashboardStatsUseCase(userId).first()

        // Then
        assertNotNull(stats)
        assertEquals(user, stats?.user)
        assertEquals(target, stats?.activeTarget)
        assertEquals(2, stats?.todayRecitationCount)
        assertEquals(1, stats?.streakDays)
        assertTrue(stats?.childrenProgress?.isEmpty() == true)
    }

    @Test
    fun `home keeps the latest completed goal visible until it is replaced`() = runTest {
        val user = UserProfile(id = 1L, name = "Meera", profileType = ProfileType.INDIVIDUAL, avatarIndex = 0)
        val completed = Target(userId = 1, setByUserId = 1, targetRecitationCount = 108,
            currentRecitationCount = 108, isCompleted = true, startedAtMillis = 200)
        val previous = completed.copy(stableId = "previous", startedAtMillis = 100, isActive = false)
        coEvery { userRepository.getById(1L) } returns user
        every { targetRepository.getAllForUser(1L) } returns flowOf(listOf(previous, completed))
        every { recitationRepository.getSessionsForUser(1L) } returns flowOf(emptyList())
        every { userRepository.getChildrenOf(1L) } returns flowOf(emptyList())
        assertEquals(completed, getDashboardStatsUseCase(1L).first()!!.activeTarget)
    }

    // --- the practice streak ----------------------------------------------------

    /** Millis at midday on the day [daysAgo] days before today, in the local zone. */
    private fun daysAgo(daysAgo: Long): Long =
        LocalDate.now(ZoneId.systemDefault())
            .minusDays(daysAgo)
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun session(createdAt: Long, isCorrect: Boolean = true) = RecitationSession(
        userId = 1L,
        mantraId = gayatri.id,
        mode = RecitationMode.NORMAL,
        overallScore = if (isCorrect) 1.0 else 0.4,
        wordScores = emptyMap(),
        transcription = "",
        isCorrect = isCorrect,
        durationMs = 1000L,
        createdAt = createdAt
    )

    private suspend fun streakFor(sessions: List<RecitationSession>): Int {
        val user = UserProfile(id = 1L, name = "John", profileType = ProfileType.INDIVIDUAL, avatarIndex = 0)
        coEvery { userRepository.getById(1L) } returns user
        every { targetRepository.getAllForUser(1L) } returns flowOf(emptyList())
        every { recitationRepository.getSessionsForUser(1L) } returns flowOf(sessions)
        every { userRepository.getChildrenOf(1L) } returns flowOf(emptyList())
        return getDashboardStatsUseCase(1L).first()!!.streakDays
    }

    @Test
    fun `streak counts consecutive calendar days back from today`() = runTest {
        assertEquals(3, streakFor(listOf(session(daysAgo(0)), session(daysAgo(1)), session(daysAgo(2)))))
    }

    @Test
    fun `streak survives several recitations on the same day`() = runTest {
        assertEquals(
            2,
            streakFor(listOf(session(daysAgo(0)), session(daysAgo(0)), session(daysAgo(1))))
        )
    }

    @Test
    fun `streak still stands the morning after, before today's recitation`() = runTest {
        assertEquals(2, streakFor(listOf(session(daysAgo(1)), session(daysAgo(2)))))
    }

    @Test
    fun `streak stops at the first missed day`() = runTest {
        assertEquals(2, streakFor(listOf(session(daysAgo(0)), session(daysAgo(1)), session(daysAgo(3)))))
    }

    @Test
    fun `streak is zero once two days have been missed`() = runTest {
        assertEquals(0, streakFor(listOf(session(daysAgo(2)), session(daysAgo(3)))))
    }

    @Test
    fun `streak ignores recitations that were not verified`() = runTest {
        assertEquals(
            1,
            streakFor(listOf(session(daysAgo(0)), session(daysAgo(1), isCorrect = false)))
        )
    }
}
