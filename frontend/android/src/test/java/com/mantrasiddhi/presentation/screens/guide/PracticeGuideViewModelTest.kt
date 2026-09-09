package com.mantrasiddhi.presentation.screens.guide

import androidx.lifecycle.SavedStateHandle
import com.mantrasiddhi.data.preferences.PracticeGuidePreferences
import com.mantrasiddhi.data.preferences.PracticeGuideRecord
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeGuideViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val users = MutableStateFlow<UserProfile?>(UserProfile(1, "Meera", ProfileType.INDIVIDUAL, avatarIndex = 0))
    private val record = MutableStateFlow(PracticeGuideRecord(1))
    private val otherRecord = MutableStateFlow(PracticeGuideRecord(1))
    private val store = mockk<PracticeGuidePreferences>(relaxed = true)
    private val getUser = mockk<GetCurrentUserUseCase>()
    private val saved = SavedStateHandle()

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { getUser() } returns users
        every { store.observe(1, 1) } returns record
        every { store.observe(2, 1) } returns otherRecord
        coEvery { store.markShown(1, 1, any()) } coAnswers {
            record.value = record.value.copy(shownAt = 10, openedAt = if (thirdArg()) 11 else null)
        }
        coEvery { store.acknowledge(1, 1) } coAnswers {
            record.value = record.value.copy(acknowledgedAt = 12)
        }
    }
    @AfterEach fun teardown() { Dispatchers.resetMain() }
    private fun model() = PracticeGuideViewModel(getUser, store, saved)

    @Test fun `skipping first use never acknowledges or opens the panel again`() = runTest(dispatcher) {
        val vm = model()
        vm.offerFirstUse()
        assertTrue(vm.state.value.visible)
        assertNull(record.value.openedAt)
        vm.dismiss()
        vm.offerFirstUse()
        assertFalse(vm.state.value.visible)
        assertTrue(vm.state.value.needsReview)
        coVerify(exactly = 0) { store.acknowledge(any(), any()) }
        assertFalse(model().state.value.firstUse)
    }

    @Test fun `manual opening is separate from explicit acknowledgement`() = runTest(dispatcher) {
        val vm = model()
        vm.open()
        assertNotNull(record.value.openedAt)
        assertNull(record.value.acknowledgedAt)
        vm.acknowledge()
        assertFalse(vm.state.value.visible)
        assertFalse(vm.state.value.needsReview)
        vm.open()
        assertTrue(vm.state.value.visible)
        assertNotNull(vm.state.value.record?.acknowledgedAt)
    }

    @Test fun `restored open panel belongs only to its profile`() = runTest(dispatcher) {
        saved["guide_profile"] = 1L
        val vm = model()
        assertTrue(vm.state.value.visible)
        users.value = UserProfile(2, "Ravi", ProfileType.INDIVIDUAL, avatarIndex = 0)
        assertFalse(vm.state.value.visible)
        assertTrue(vm.state.value.firstUse)
        vm.acknowledge()
        coVerify { store.acknowledge(2, 1) }
        coVerify(exactly = 0) { store.acknowledge(1, 1) }
    }

    @Test fun `failed acknowledgement stays open and can be retried`() = runTest(dispatcher) {
        coEvery { store.acknowledge(1, 1) } throws java.io.IOException("disk")
        val vm = model()
        vm.open()
        vm.acknowledge()
        assertTrue(vm.state.value.visible)
        assertTrue(vm.state.value.saveError)
        assertFalse(vm.state.value.saving)
        assertNull(record.value.acknowledgedAt)
        coEvery { store.acknowledge(1, 1) } returns Unit
        vm.acknowledge()
        assertFalse(vm.state.value.visible)
    }

    @Test fun `repeated taps cannot start duplicate acknowledgement writes`() = runTest(dispatcher) {
        val write = CompletableDeferred<Unit>()
        coEvery { store.acknowledge(1, 1) } coAnswers { write.await() }
        val vm = model()
        vm.open()
        vm.acknowledge()
        vm.acknowledge()
        coVerify(exactly = 1) { store.acknowledge(1, 1) }
        write.complete(Unit)
        assertFalse(vm.state.value.saving)
    }

    @Test fun `old profile write cannot close a new profiles guide`() = runTest(dispatcher) {
        val write = CompletableDeferred<Unit>()
        coEvery { store.acknowledge(1, 1) } coAnswers { write.await() }
        val vm = model()
        vm.open()
        vm.acknowledge()
        users.value = UserProfile(2, "Ravi", ProfileType.INDIVIDUAL, avatarIndex = 0)
        vm.open()
        write.complete(Unit)
        assertTrue(vm.state.value.visible)
        assertEquals(2L, vm.state.value.profileId)
    }
}
