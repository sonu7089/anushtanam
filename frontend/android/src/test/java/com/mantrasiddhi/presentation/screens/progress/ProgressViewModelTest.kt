package com.mantrasiddhi.presentation.screens.progress

import androidx.lifecycle.ViewModelStore
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.*
import com.mantrasiddhi.domain.usecase.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val user = MutableStateFlow<UserProfile?>(UserProfile(1, "Fixture", ProfileType.INDIVIDUAL, avatarIndex = 0))
    private val current = mockk<GetCurrentUserUseCase> { every { this@mockk.invoke() } returns user }
    private val stats = mockk<GetRecitationStatsUseCase>()
    private val sessions = mockk<RecitationRepository>()
    private val cloud = mockk<ProgressSyncRepository>()
    private val japa = mockk<GetJapaSummaryUseCase>()
    private lateinit var vm: ProgressViewModel
    private val active = mutableMapOf<String, Int>()
    private fun <T> counted(key: String, value: T) = flow {
        active[key] = (active[key] ?: 0) + 1
        try { emit(value); awaitCancellation() } finally { active[key] = active.getValue(key) - 1 }
    }
    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { stats(any()) } returns counted("stats", RecitationOverviewStats(100, 80, .8, 3, .99))
        every { cloud.summaryFlow() } returns counted("cloud", null)
        every { japa(any()) } returns counted("japa", JapaSummary(mapOf(LocalDate.now() to java.math.BigInteger.valueOf(740))))
        every { sessions.observeScoreDays(any(), any(), any()) } returns counted("scores", emptyList())
        every { sessions.observeHistoryPage(any(), any(), any(), any()) } answers {
            val before = thirdArg<Long>()
            val rows = (1L..80L).reversed().filter { it < before }.take(arg(3))
                .map { SessionHistoryRow(it, 100, "NORMAL", .9, true, 1000) }
            counted("history", rows)
        }
        vm = ProgressViewModel(current, stats, sessions, cloud, japa)
    }
    @AfterEach fun cleanup() { ViewModelStore().apply { put("progress", vm); clear() }; Dispatchers.resetMain() }
    private fun state() = vm.uiState.value as ProgressUiState.Success

    @Test fun `only selected projections run and hidden UI cancels every read`() = runTest(dispatcher) {
        assertTrue(active.isEmpty())
        val observer = backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        assertEquals("740", state().overviewStats.performedJapa)
        verify(exactly = 0) { sessions.observeHistoryPage(any(), any(), any(), any()); sessions.observeScoreDays(any(), any(), any()) }
        user.value = user.value!!.copy(name = "Renamed")
        verify(exactly = 1) { stats(any()) }
        vm.selectTab(2)
        assertEquals(0, active["stats"])
        assertEquals(0, active["japa"])
        assertEquals(1, active["scores"])
        assertEquals(7, state().axisTrends.size)
        vm.selectTab(2)
        verify(exactly = 1) { sessions.observeScoreDays(any(), any(), any()) }
        observer.cancel(); runCurrent()
        assertTrue(active.values.all { it == 0 })
    }

    @Test fun `history pages use stable cursor retain rows and retry failed append`() = runTest(dispatcher) {
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        vm.selectTab(3); runCurrent()
        assertEquals(30, state().sessions.size)
        vm.loadMore(); runCurrent()
        assertEquals(60, state().sessions.size)
        assertEquals(60, state().sessions.map { it.id }.distinct().size)
        every { sessions.observeHistoryPage(1, 100, 21, 31) } returns flow { error("offline") }
        vm.loadMore(); runCurrent()
        assertEquals(60, state().sessions.size)
        assertTrue(state().error)
        every { sessions.observeHistoryPage(1, 100, 21, 31) } returns flowOf(
            (20L downTo 1).map { SessionHistoryRow(it, 100, "NORMAL", .9, true, 1000) })
        vm.loadMore(); runCurrent()
        assertEquals(80, state().sessions.size)
        assertFalse(state().hasMore)
        vm.selectTab(0); vm.selectTab(3); runCurrent()
        assertEquals(80, state().sessions.size)
    }

    @Test fun `account replacement clears cached results before slow new account responds`() = runTest(dispatcher) {
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        assertEquals(100, state().overviewStats.totalSessions)
        every { stats(2) } returns flow { awaitCancellation() }
        user.value = user.value!!.copy(id = 2)
        runCurrent()
        assertEquals(ProgressUiState.Loading, vm.uiState.value)
        assertEquals(0, active["stats"])
    }

    @Test fun `cloud overview bypasses local reads and daily count needs no history`() = runTest(dispatcher) {
        every { cloud.summaryFlow() } returns flowOf(ProgressSummary(40, 30, 32.0, .97,
            mapOf(LocalDate.now().toString() to DayAggregate(4, 3, 3.2))))
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        assertEquals(40, state().overviewStats.totalSessions)
        verify(exactly = 0) { stats(any()) }
        vm.selectTab(1)
        assertEquals(740, state().dailyCounts.last().count)
        verify(exactly = 0) { sessions.observeHistoryPage(any(), any(), any(), any()); sessions.observeScoreDays(any(), any(), any()) }
    }

    @Test fun `initial failure retries without blocking tab navigation`() = runTest(dispatcher) {
        every { stats(any()) } returns flow { error("failed") }
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value is ProgressUiState.Error)
        vm.selectTab(1)
        assertEquals(7, state().dailyCounts.size)
        vm.selectTab(0)
        every { stats(any()) } returns flowOf(RecitationOverviewStats(2, 1, .5, 1, .5))
        vm.retry()
        assertEquals(2, state().overviewStats.totalSessions)
    }

    @Test fun `cloud updates keep the same japa producer and cancel unused local fallback`() = runTest(dispatcher) {
        val summary = MutableStateFlow<ProgressSummary?>(null)
        every { cloud.summaryFlow() } returns summary
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        summary.value = ProgressSummary(40, 30, 32.0, .97, emptyMap())
        summary.value = summary.value!!.copy(totalSessions = 41)
        assertEquals(41, state().overviewStats.totalSessions)
        assertEquals(0, active["stats"])
        assertEquals(1, active["japa"])
        verify(exactly = 1) { japa(1) }
    }
}
