package com.mantrasiddhi.presentation.screens.sandhya

import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.data.preferences.SandhyaSettings
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.SandhyaCountRepository
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SandhyaCountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<SandhyaCountRepository>(relaxed = true)
    private val preferences = mockk<SandhyaPreferences>()
    private val current = MutableStateFlow<UserProfile?>(UserProfile(7, "Meera", ProfileType.INDIVIDUAL, null, 0))
    private val currentUser = mockk<GetCurrentUserUseCase>()
    private lateinit var model: SandhyaCountViewModel

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { currentUser() } returns current
        coEvery { repository.getDraft(any()) } returns null
        coEvery { repository.getCount(any(), any(), any()) } returns null
        coEvery { repository.history(any(), any(), any()) } returns emptyList()
        coEvery { preferences.current() } returns SandhyaSettings(false, SandhyaRegions.DEFAULT, 7)
        model = SandhyaCountViewModel(repository, preferences, currentUser)
    }

    @AfterEach fun teardown() { Dispatchers.resetMain() }

    @Test fun counterPersists57WithoutCreditAndCanResume() = runTest(dispatcher) {
        model.open(SandhyaPeriod.SAYAM, LocalDate.of(2026, 9, 7), SandhyaCountMode.COUNTER)
        runCurrent()
        repeat(57) { model.step(1) }
        runCurrent()
        val draft = model.editor.value!!.draft
        assertEquals("57", draft.input)
        coVerify { repository.saveDraft(match { it.input == "57" && it.period == SandhyaPeriod.SAYAM }) }
        coVerify(exactly = 0) { repository.save(any()) }
        model.dismiss()
        coEvery { repository.getDraft(7) } returns draft
        model.open(SandhyaPeriod.PRATAH, LocalDate.of(2026, 9, 8), SandhyaCountMode.COUNTER)
        runCurrent()
        assertEquals(draft, model.editor.value!!.draft)
        assertTrue(model.editor.value!!.paused)
        model.step(1)
        assertEquals("57", model.editor.value!!.draft.input)
        model.togglePause()
        model.step(108)
        model.undo()
        assertEquals("57", model.editor.value!!.draft.input)
    }

    @Test fun duplicateSaveUsesOneOperationAndLatestInput() = runTest(dispatcher) {
        model.open(SandhyaPeriod.SAYAM, LocalDate.of(2026, 9, 7), SandhyaCountMode.RECORD)
        runCurrent()
        coEvery { repository.save(any()) } coAnswers {
            val draft = firstArg<SandhyaCountDraft>()
            SandhyaCountChange(draft.id, draft.userId, draft.dateKey, draft.period, draft.zoneId,
                draft.mode, null, draft.newTotal!!, 1, 1)
        }
        model.changeInput("600")
        model.save()
        model.save()
        model.changeInput("700")
        runCurrent()
        coVerify(exactly = 1) { repository.save(match { it.input == "600" }) }
        assertNull(model.editor.value)
    }

    @Test fun switchingProfileClosesEditorAndDoesNotCreditEitherProfile() = runTest(dispatcher) {
        model.open(SandhyaPeriod.SAYAM, LocalDate.of(2026, 9, 7), SandhyaCountMode.RECORD)
        runCurrent()
        model.changeInput("600")
        current.value = current.value!!.copy(id = 8)
        model.save()
        runCurrent()
        assertNull(model.editor.value)
        coVerify(exactly = 0) { repository.save(any()) }
        coVerify(exactly = 0) { repository.saveDraft(match { it.userId == 8L }) }
    }

    @Test fun editShowsAbsoluteTotalAndKeepsFailedSaveAvailable() = runTest(dispatcher) {
        coEvery { repository.getCount(7, any(), any()) } returns
            SandhyaCount(7, "20260907", SandhyaPeriod.SAYAM, 600, 1, "Asia/Kolkata")
        model.open(SandhyaPeriod.SAYAM, LocalDate.of(2026, 9, 7), SandhyaCountMode.EDIT)
        runCurrent()
        model.changeInput("500")
        assertEquals(500L, model.editor.value!!.draft.newTotal)
        coEvery { repository.save(any()) } throws IllegalStateException("disk")
        model.save()
        runCurrent()
        assertEquals("500", model.editor.value!!.draft.input)
        assertFalse(model.editor.value!!.saving)
        assertNotNull(model.editor.value!!.errorRes)
    }

    @Test fun staleRecordButtonOpensSavedTotalWithoutResettingIt() = runTest(dispatcher) {
        coEvery { repository.getCount(7, any(), any()) } returns
            SandhyaCount(7, "20260907", SandhyaPeriod.SAYAM, 600, 1, "Asia/Kolkata")
        model.open(SandhyaPeriod.SAYAM, LocalDate.of(2026, 9, 7), SandhyaCountMode.RECORD)
        runCurrent()
        assertEquals(SandhyaCountMode.EDIT, model.editor.value!!.draft.mode)
        assertEquals("600", model.editor.value!!.draft.input)
    }

    @Test fun draftDateChangesOnlyWhenChosenAndKeepsItsCount() = runTest(dispatcher) {
        model.open(SandhyaPeriod.SAYAM, LocalDate.of(2026, 9, 6), SandhyaCountMode.COUNTER)
        runCurrent()
        model.step(57)
        assertEquals("20260906", model.editor.value!!.draft.dateKey)
        coEvery { repository.getCount(7, "20260907", SandhyaPeriod.SAYAM) } returns
            SandhyaCount(7, "20260907", SandhyaPeriod.SAYAM, 100, 1, "Asia/Kolkata")
        model.changeDate(LocalDate.of(2026, 9, 7))
        runCurrent()
        assertEquals("57", model.editor.value!!.draft.input)
        assertEquals("20260907", model.editor.value!!.draft.dateKey)
        assertEquals(157L, model.editor.value!!.draft.newTotal)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test fun minimumsFollowTheActiveProfileAndUpdateWithoutChangingCounts() = runTest(dispatcher) {
        val minimums = MutableStateFlow(SandhyaMinimums(216, 54, 600))
        every { preferences.minimums(7) } returns minimums
        every { preferences.minimums(8) } returns flowOf(SandhyaMinimums())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.minimums.collect {} }
        runCurrent()
        assertEquals(600L, model.minimums.value.sayam)
        minimums.value = SandhyaMinimums(216, 54, 500)
        runCurrent()
        assertEquals(500L, model.minimums.value.sayam)
        current.value = current.value!!.copy(id = 8)
        runCurrent()
        assertEquals(SandhyaMinimums(), model.minimums.value)
        coVerify(exactly = 0) { repository.save(any()) }
    }
}
