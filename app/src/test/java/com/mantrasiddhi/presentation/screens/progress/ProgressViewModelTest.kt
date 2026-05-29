/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.progress

import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.GetAccuracyTrendUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.GetDailyRecitationCountsUseCase
import com.mantrasiddhi.domain.usecase.GetRecitationStatsUseCase
import com.mantrasiddhi.domain.usecase.GetSessionsForUserUseCase
import com.mantrasiddhi.domain.usecase.GetWordMasteryUseCase
import com.mantrasiddhi.domain.usecase.RecitationOverviewStats
import io.mockk.coEvery
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
    private lateinit var getWordMasteryUseCase: GetWordMasteryUseCase
    private lateinit var getSessionsForUserUseCase: GetSessionsForUserUseCase

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
        getWordMasteryUseCase = mockk()
        getSessionsForUserUseCase = mockk()
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
            getWordMasteryUseCase,
            getSessionsForUserUseCase
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
        coEvery { getWordMasteryUseCase(testUser.id) } returns flowOf(emptyList())
        coEvery { getSessionsForUserUseCase(testUser.id) } returns flowOf(emptyList())

        viewModel = ProgressViewModel(
            getCurrentUserUseCase,
            getRecitationStatsUseCase,
            getDailyRecitationCountsUseCase,
            getAccuracyTrendUseCase,
            getWordMasteryUseCase,
            getSessionsForUserUseCase
        )

        val job = launch {
            viewModel.uiState.collect {}
        }
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is ProgressUiState.Success)
        val successState = state as ProgressUiState.Success
        assertEquals(overview, successState.overviewStats)
        assertTrue(successState.dailyCounts.isEmpty())
        assertTrue(successState.accuracyTrend.isEmpty())
        assertTrue(successState.wordMastery.isEmpty())
        assertTrue(successState.sessions.isEmpty())
        job.cancel()

    }
}
