/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.home

import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.DashboardStats
import com.mantrasiddhi.domain.usecase.GetActivePurashcharanaUseCase
import com.mantrasiddhi.domain.usecase.GetDashboardStatsUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var getDashboardStatsUseCase: GetDashboardStatsUseCase
    private lateinit var getActivePurashcharanaUseCase: GetActivePurashcharanaUseCase
    private lateinit var viewModel: HomeViewModel

    private val testUser = UserProfile(
        id = 1L,
        name = "Meera",
        profileType = ProfileType.PARENT,
        parentId = null,
        avatarIndex = 1
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getCurrentUserUseCase = mockk()
        getDashboardStatsUseCase = mockk()
        // The dashboard must not depend on the Purashcharana card; every test here runs
        // with "no sankalpa taken", which is also the common case.
        getActivePurashcharanaUseCase = mockk()
        every { getActivePurashcharanaUseCase(any()) } returns flowOf(null)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should_initiallyEmitLoadingState_when_created`() = runTest {
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(null)
        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase)
        assertTrue(viewModel.uiState.value is HomeUiState.Error)
    }

    @Test
    fun `should_emitSuccessState_when_statsLoadedSuccessfully`() = runTest {
        val stats = DashboardStats(
            user = testUser,
            todayRecitationCount = 5,
            streakDays = 3,
            activeTarget = Target(id = 1L, userId = 1L, setByUserId = 1L, targetRecitationCount = 108, currentRecitationCount = 20, rewardType = RewardType.MONEY, rewardDescription = "₹100 pocket money", isCompleted = false),
            childrenProgress = emptyList()
        )

        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(testUser)
        coEvery { getDashboardStatsUseCase(testUser.id) } returns flowOf(stats)

        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase)

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Success)
        val successState = state as HomeUiState.Success
        assertEquals(testUser, successState.user)
        assertEquals(5, successState.todayCount)
        assertEquals(3, successState.streakDays)
        assertEquals(stats.activeTarget, successState.activeTarget)
    }

    @Test
    fun `should_emitErrorState_when_statsReturnNull`() = runTest {
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(testUser)
        coEvery { getDashboardStatsUseCase(testUser.id) } returns flowOf(null)

        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase)

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Error)
        assertEquals(R.string.home_error_load_failed, (state as HomeUiState.Error).messageResId)
    }
}
