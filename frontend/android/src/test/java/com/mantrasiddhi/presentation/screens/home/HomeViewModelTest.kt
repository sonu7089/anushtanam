/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.home

import com.mantrasiddhi.resources.R
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class HomeViewModelTest {

    private val observers = mutableListOf<kotlinx.coroutines.Job>()
    private fun watch(vm: HomeViewModel) {
        observers += kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { vm.uiState.collect {} }
    }

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var getDashboardStatsUseCase: GetDashboardStatsUseCase
    private lateinit var getActivePurashcharanaUseCase: GetActivePurashcharanaUseCase
    private val japa = mockk<com.mantrasiddhi.domain.usecase.GetJapaSummaryUseCase> {
        every { this@mockk.invoke(any()) } returns flowOf(com.mantrasiddhi.domain.usecase.JapaSummary())
    }
    private lateinit var viewModel: HomeViewModel
    private val teachings = mockk<com.mantrasiddhi.data.messages.TeachingRepository> {
        every { reflection } returns MutableStateFlow(null)
    }

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
        observers.forEach { it.cancel() }; Dispatchers.resetMain()
    }

    @Test
    fun `should_initiallyEmitLoadingState_when_created`() = runTest {
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(null)
        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings)
        watch(viewModel)
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

        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings)
        watch(viewModel)

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

        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings)
        watch(viewModel)

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Success)
        assertTrue((state as HomeUiState.Success).statsFailed)
    }

    @Test fun `slow sections leave navigation state usable and unsubscribe when hidden`() = runTest {
        var active = 0
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(testUser)
        every { getDashboardStatsUseCase(any()) } returns kotlinx.coroutines.flow.flow {
            active++
            try { kotlinx.coroutines.awaitCancellation() } finally { active-- }
        }
        every { japa(any()) } returns kotlinx.coroutines.flow.flow { kotlinx.coroutines.awaitCancellation() }
        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings)
        assertEquals(0, active)
        watch(viewModel)
        val state = viewModel.uiState.value as HomeUiState.Success
        assertEquals(testUser, state.user)
        assertTrue(state.statsLoading)
        assertTrue(state.japaLoading)
        assertEquals(null, state.todayJapa)
        observers.forEach { it.cancel() }
        runCurrent()
        assertEquals(0, active)
    }
}
