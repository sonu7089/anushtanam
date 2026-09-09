package com.mantrasiddhi.presentation.screens.guide

import androidx.lifecycle.SavedStateHandle
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeGuideViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val users = MutableStateFlow<UserProfile?>(UserProfile(1, "Meera", ProfileType.INDIVIDUAL, avatarIndex = 0))
    private val getUser = mockk<GetCurrentUserUseCase>()
    private val saved = SavedStateHandle()

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { getUser() } returns users
    }
    @AfterEach fun teardown() { Dispatchers.resetMain() }
    private fun model() = PracticeGuideViewModel(getUser, saved)

    @Test fun `guide opens only on request and stays closed after dismissal and recreation`() = runTest(dispatcher) {
        val vm = model()
        assertFalse(vm.state.value.visible)
        vm.open()
        assertTrue(vm.state.value.visible)
        vm.dismiss()
        assertFalse(model().state.value.visible)
        vm.open()
        assertTrue(vm.state.value.visible)
    }

    @Test fun `open guide is restored after activity recreation`() = runTest(dispatcher) {
        model().open()
        assertTrue(model().state.value.visible)
    }

    @Test fun `profile changes close the guide and do not reopen it on return`() = runTest(dispatcher) {
        val vm = model()
        vm.open()
        val firstUser = users.value
        users.value = UserProfile(2, "Ravi", ProfileType.INDIVIDUAL, avatarIndex = 0)
        assertFalse(vm.state.value.visible)
        vm.open()
        assertTrue(vm.state.value.visible)
        users.value = firstUser
        assertFalse(vm.state.value.visible)
        assertFalse(model().state.value.visible)
    }

    @Test fun `restored guide waits for the saved profile to load`() = runTest(dispatcher) {
        saved["guide_profile"] = 1L
        val user = users.value
        users.value = null
        val vm = model()
        assertFalse(vm.state.value.visible)
        users.value = user
        assertTrue(vm.state.value.visible)
    }

    @Test fun `logout clears an open guide`() = runTest(dispatcher) {
        val vm = model()
        vm.open()
        users.value = null
        assertFalse(vm.state.value.visible)
        vm.open()
        assertFalse(vm.state.value.visible)
        assertNull(saved.get<Long>("guide_profile"))
    }
}
