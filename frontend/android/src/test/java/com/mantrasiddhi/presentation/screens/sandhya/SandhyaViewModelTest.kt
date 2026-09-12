package com.mantrasiddhi.presentation.screens.sandhya

import androidx.lifecycle.ViewModelStore
import com.mantrasiddhi.data.preferences.*
import com.mantrasiddhi.data.sandhya.*
import com.mantrasiddhi.domain.diagnostics.WorkProbe
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.*
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class SandhyaViewModelTest {
    @Test fun `draft edits and idle minutes do not rebuild calendar or request scheduling and hidden reads stop`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val settings = SandhyaSettings(true, SandhyaRegions.DEFAULT, 7)
        val prefs = mockk<SandhyaPreferences> {
            every { this@mockk.settings } returns MutableStateFlow(settings)
            coEvery { current() } returns settings
            every { minimums(7) } returns flowOf(SandhyaMinimums())
            every { deliveryChanges(7) } returns flowOf(emptyMap())
        }
        val scheduler = mockk<SandhyaScheduler> {
            coEvery { upcoming(any(), any(), any()) } returns emptyMap()
        }
        var subscriptions = 0
        val logs = mockk<SandhyaLogRepository> {
            every { observeLogs(7) } returns flow {
                subscriptions++
                try { emit(emptyList()); awaitCancellation() } finally { subscriptions-- }
            }
            coEvery { sweepMissed(any(), any()) } just Runs
        }
        val draft = MutableStateFlow<SandhyaCountDraft?>(null)
        val counts = mockk<SandhyaCountRepository> {
            every { observeCounts(7) } returns flowOf(emptyList())
            every { observeDraft(7) } returns draft
        }
        val current = mockk<GetCurrentUserUseCase> {
            every { this@mockk.invoke() } returns MutableStateFlow(UserProfile(7, "Fixture", ProfileType.INDIVIDUAL, avatarIndex = 0))
        }
        val vm = SandhyaViewModel(prefs, scheduler, mockk(), logs, counts, current)
        val enabled = WorkProbe.enabled
        WorkProbe.enabled = true
        var watch = backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        try {
            runCurrent()
            assertFalse(vm.uiState.value.loading)
            assertEquals(1, subscriptions)
            val builds = WorkProbe.snapshot().first["sandhya.base.build"]
            repeat(20) {
                draft.value = SandhyaCountDraft("draft", 7, "20260912", SandhyaPeriod.PRATAH,
                    settings.zone().id, SandhyaCountMode.RECORD, "$it", null, 0, it.toLong())
                runCurrent()
            }
            advanceTimeBy(120_000); runCurrent()
            assertEquals("19", vm.uiState.value.draft?.input)
            assertEquals(builds, WorkProbe.snapshot().first["sandhya.base.build"])
            coVerify(exactly = 1) { scheduler.upcoming(any(), any(), any()) }
            coVerify(exactly = 0) { scheduler.scheduleAll(any()); prefs.setUserId(any()) }
            val yesterday = java.time.LocalDate.now(settings.zone()).minusDays(1)
            vm.selectDate(yesterday); runCurrent()
            assertEquals(yesterday, vm.uiState.value.date)
            watch.cancel(); runCurrent()
            assertEquals(0, subscriptions)
            watch = backgroundScope.launch(dispatcher) { vm.uiState.collect {} }; runCurrent()
            assertEquals(yesterday, vm.uiState.value.date)
        } finally {
            watch.cancel()
            ViewModelStore().apply { put("test", vm); clear() }
            WorkProbe.enabled = enabled
            Dispatchers.resetMain()
        }
    }
}
