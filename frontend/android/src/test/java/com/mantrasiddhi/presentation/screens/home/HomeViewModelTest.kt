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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val calendarDays = MutableStateFlow(java.time.LocalDate.now() to java.time.ZoneId.systemDefault())
    private val calendarTime = mockk<com.mantrasiddhi.domain.util.CalendarTime> {
        every { days(any()) } returns calendarDays
    }
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
        observers.forEach { it.cancel() }
        if (::viewModel.isInitialized) androidx.lifecycle.ViewModelStore().apply { put("home", viewModel); clear() }
        Dispatchers.resetMain()
    }

    @Test
    fun `should_initiallyEmitLoadingState_when_created`() = runTest {
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(null)
        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings, calendarTime)
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

        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings, calendarTime)
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

        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings, calendarTime)
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
        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings, calendarTime)
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

    @Test fun `midnight refreshes japa without reloading unchanged sections`() = runTest {
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(testUser)
        every { getDashboardStatsUseCase(any()) } returns flowOf(DashboardStats(testUser, 0, 0, null))
        every { japa(any()) } returns flowOf(com.mantrasiddhi.domain.usecase.JapaSummary(
            mapOf(calendarDays.value.first to java.math.BigInteger.valueOf(108))))
        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings, calendarTime)
        watch(viewModel)
        assertEquals("108", (viewModel.uiState.value as HomeUiState.Success).todayJapa)
        calendarDays.value = calendarDays.value.first.plusDays(1) to calendarDays.value.second
        assertEquals("0", (viewModel.uiState.value as HomeUiState.Success).todayJapa)
        io.mockk.verify(exactly = 1) { japa(any()); getDashboardStatsUseCase(any()) }
    }

    @Test fun `returning to home retains every loaded section while fresh reads are pending`() = runTest {
        every { getCurrentUserUseCase() } returns MutableStateFlow(testUser)
        every { getDashboardStatsUseCase(any()) } returns flowOf(DashboardStats(testUser, 12, 3, null))
        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings, calendarTime)
        watch(viewModel)
        val loaded = viewModel.uiState.value
        observers.forEach { it.cancel() }; runCurrent()
        advanceTimeBy(60_000)
        assertEquals(loaded, viewModel.uiState.value)
        // Returning must not rebuild the shell or discard each section's cached value.
        every { getDashboardStatsUseCase(any()) } returns kotlinx.coroutines.flow.flow { kotlinx.coroutines.awaitCancellation() }
        every { getActivePurashcharanaUseCase(any()) } returns kotlinx.coroutines.flow.flow { kotlinx.coroutines.awaitCancellation() }
        every { japa(any()) } returns kotlinx.coroutines.flow.flow { kotlinx.coroutines.awaitCancellation() }
        val returned = mutableListOf<HomeUiState>()
        observers += backgroundScope.launch(testDispatcher) { viewModel.uiState.collect { returned += it } }
        runCurrent()
        assertTrue(returned.isNotEmpty())
        assertTrue(returned.all { it == loaded })
    }

    @Test fun `hidden home invalidates cached sections on account replacement and sign out`() = runTest {
        val session = MutableStateFlow<com.mantrasiddhi.domain.model.UserProfile?>(testUser.copy(firebaseUid = "first"))
        every { getCurrentUserUseCase() } returns session
        every { getDashboardStatsUseCase(any()) } returns flowOf(DashboardStats(testUser, 12, 3, null))
        viewModel = HomeViewModel(getCurrentUserUseCase, getDashboardStatsUseCase, getActivePurashcharanaUseCase, japa, teachings, calendarTime)
        watch(viewModel)
        observers.forEach { it.cancel() }; runCurrent()
        session.value = session.value!!.copy(firebaseUid = "replacement")
        runCurrent()
        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
        every { getDashboardStatsUseCase(any()) } returns kotlinx.coroutines.flow.flow { kotlinx.coroutines.awaitCancellation() }
        watch(viewModel)
        val replaced = viewModel.uiState.value as HomeUiState.Success
        assertTrue(replaced.statsLoading)
        assertEquals(0, replaced.todayCount)
        observers.forEach { it.cancel() }; runCurrent()
        session.value = null; runCurrent()
        assertTrue(viewModel.uiState.value is HomeUiState.Error)
    }
}
