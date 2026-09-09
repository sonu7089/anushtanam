/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.progress

import com.mantrasiddhi.domain.model.DayAggregate
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.ProgressSummary
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.ProgressSyncRepository
import com.mantrasiddhi.domain.usecase.GetAccuracyTrendUseCase
import com.mantrasiddhi.domain.usecase.GetAxisTrendsUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.GetDailyRecitationCountsUseCase
import com.mantrasiddhi.domain.usecase.GetRecitationStatsUseCase
import com.mantrasiddhi.domain.usecase.GetSessionsForUserUseCase
import com.mantrasiddhi.domain.usecase.RecitationOverviewStats
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var getRecitationStatsUseCase: GetRecitationStatsUseCase
    private lateinit var getDailyRecitationCountsUseCase: GetDailyRecitationCountsUseCase
    private lateinit var getAccuracyTrendUseCase: GetAccuracyTrendUseCase
    private lateinit var getSessionsForUserUseCase: GetSessionsForUserUseCase
    private lateinit var getAxisTrendsUseCase: GetAxisTrendsUseCase
    private lateinit var progressSyncRepository: ProgressSyncRepository

    private val japa = mockk<com.mantrasiddhi.domain.usecase.GetJapaSummaryUseCase> {
        every { this@mockk.invoke(any()) } returns flowOf(com.mantrasiddhi.domain.usecase.JapaSummary(mapOf(java.time.LocalDate.now() to java.math.BigInteger.valueOf(740))))
    }
    private lateinit var viewModel: ProgressViewModel

    private val testUser = UserProfile(
        id = 1L,
        name = "Aarav",
        profileType = ProfileType.CHILD,
        parentId = 2L,
        avatarIndex = 2
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getCurrentUserUseCase = mockk()
        getRecitationStatsUseCase = mockk()
        getDailyRecitationCountsUseCase = mockk()
        getAccuracyTrendUseCase = mockk()
        getSessionsForUserUseCase = mockk()
        getAxisTrendsUseCase = mockk()
        progressSyncRepository = mockk()
        // Default: no cloud summary -> the local aggregates drive the UI unchanged.
        every { progressSyncRepository.summaryFlow() } returns flowOf(null)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should_initiallyEmitLoadingState_when_userIsNull`() = runTest {
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(null)

        viewModel = ProgressViewModel(
            getCurrentUserUseCase,
            getRecitationStatsUseCase,
            getDailyRecitationCountsUseCase,
            getAccuracyTrendUseCase,
            getSessionsForUserUseCase,
            getAxisTrendsUseCase,
            progressSyncRepository, japa
        )

        assertTrue(viewModel.uiState.value is ProgressUiState.Loading)
    }

    @Test
    fun `should_emitSuccessState_when_allDataLoadedForActiveUser`() = runTest {
        val overview = RecitationOverviewStats(
            totalSessions = 10,
            totalCorrectRecitations = 8,
            averageAccuracy = 0.88,
            currentStreak = 5,
            bestSessionScore = 0.98
        )


        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(testUser)
        coEvery { getRecitationStatsUseCase(testUser.id) } returns flowOf(overview)
        coEvery { getDailyRecitationCountsUseCase(testUser.id) } returns flowOf(emptyList())
        coEvery { getAccuracyTrendUseCase(testUser.id) } returns flowOf(emptyList())
        coEvery { getSessionsForUserUseCase(testUser.id) } returns flowOf(emptyList())
        coEvery { getAxisTrendsUseCase(testUser.id) } returns flowOf(emptyList())

        viewModel = ProgressViewModel(
            getCurrentUserUseCase,
            getRecitationStatsUseCase,
            getDailyRecitationCountsUseCase,
            getAccuracyTrendUseCase,
            getSessionsForUserUseCase,
            getAxisTrendsUseCase,
            progressSyncRepository, japa
        )

        val job = launch {
            viewModel.uiState.collect {}
        }
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is ProgressUiState.Success)
        val successState = state as ProgressUiState.Success
        assertEquals(overview.copy(performedJapa = "740"), successState.overviewStats)
        assertTrue(successState.dailyCounts.isEmpty())
        assertTrue(successState.accuracyTrend.isEmpty())
        assertTrue(successState.sessions.isEmpty())
        job.cancel()

    }

    @Test
    fun `should_preferCloudSummary_when_summaryFlowEmitsOne`() = runTest {
        // Local aggregates carry different numbers than the cloud summary so the
        // assertion proves the cloud values won.
        val localOverview = RecitationOverviewStats(
            totalSessions = 2,
            totalCorrectRecitations = 1,
            averageAccuracy = 0.5,
            currentStreak = 1,
            bestSessionScore = 0.6
        )
        val today = java.time.LocalDate.now().toString()
        val cloudSummary = ProgressSummary(
            totalSessions = 40L,
            correctSessions = 30L,
            scoreSum = 32.0,
            bestScore = 0.97,
            daily = mapOf(today to DayAggregate(n = 4L, c = 3L, s = 3.2))
        )

        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(testUser)
        coEvery { getRecitationStatsUseCase(testUser.id) } returns flowOf(localOverview)
        coEvery { getDailyRecitationCountsUseCase(testUser.id) } returns flowOf(emptyList())
        coEvery { getAccuracyTrendUseCase(testUser.id) } returns flowOf(emptyList())
        coEvery { getSessionsForUserUseCase(testUser.id) } returns flowOf(emptyList())
        coEvery { getAxisTrendsUseCase(testUser.id) } returns flowOf(emptyList())
        every { progressSyncRepository.summaryFlow() } returns flowOf(cloudSummary)

        viewModel = ProgressViewModel(
            getCurrentUserUseCase,
            getRecitationStatsUseCase,
            getDailyRecitationCountsUseCase,
            getAccuracyTrendUseCase,
            getSessionsForUserUseCase,
            getAxisTrendsUseCase,
            progressSyncRepository, japa
        )

        val job = launch {
            viewModel.uiState.collect {}
        }
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is ProgressUiState.Success)
        val successState = state as ProgressUiState.Success

        // Overview stats come from the cloud summary, not the local aggregates.
        assertEquals(40, successState.overviewStats.totalSessions)
        assertEquals(30, successState.overviewStats.totalCorrectRecitations)
        assertEquals(0.8, successState.overviewStats.averageAccuracy, 1e-9)
        assertEquals(0.97, successState.overviewStats.bestSessionScore, 1e-9)
        assertEquals(1, successState.overviewStats.currentStreak)

        // Charts are rebuilt from the summary: a 7-day zero-filled window with
        // today's entry carrying the cloud values.
        assertEquals(7, successState.dailyCounts.size)
        assertEquals(740, successState.dailyCounts.last().count)
        assertEquals("740", successState.overviewStats.performedJapa)
        assertEquals(7, successState.accuracyTrend.size)
        assertEquals(0.8, successState.accuracyTrend.last().averageScore, 1e-9)

        // Session history stays local-only.
        assertTrue(successState.sessions.isEmpty())
        job.cancel()
    }
}
