package com.mantrasiddhi.presentation.screens.voicemodel

import com.mantrasiddhi.data.speech.VoiceModelManager
import com.mantrasiddhi.domain.model.VoiceModelState
import com.mantrasiddhi.domain.model.VoiceModelStatus
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceModelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val manager = mockk<VoiceModelManager>(relaxed = true)

    @BeforeEach fun before() { Dispatchers.setMain(dispatcher) }
    @AfterEach fun after() { Dispatchers.resetMain() }

    @Test fun `unknown support needs an explicit trial and survives permission dialog resume`() = runTest(dispatcher) {
        coEvery { manager.checkStatus() } returns VoiceModelStatus(VoiceModelState.CHECK_UNAVAILABLE)
        val vm = VoiceModelViewModel(manager)
        runCurrent()
        assertTrue(vm.uiState.value is VoiceModelUiState.NeedsModel)
        every { manager.allowUnverifiedTrial() } returns true
        every { manager.trialAllowed } returns true
        vm.tryListening()
        assertEquals(VoiceModelUiState.Ready, vm.uiState.value)
        vm.refresh(); runCurrent()
        assertEquals(VoiceModelUiState.Ready, vm.uiState.value)
    }

    @Test fun `a confirmed missing pack cannot be bypassed`() = runTest(dispatcher) {
        coEvery { manager.checkStatus() } returns VoiceModelStatus(VoiceModelState.NOT_INSTALLED)
        val vm = VoiceModelViewModel(manager)
        runCurrent(); vm.tryListening()
        assertEquals(VoiceModelState.NOT_INSTALLED, (vm.uiState.value as VoiceModelUiState.NeedsModel).state)
    }

    @Test fun `retry joins a slow check then revalidates without cancellation`() = runTest(dispatcher) {
        val old = CompletableDeferred<VoiceModelStatus>()
        var calls = 0
        coEvery { manager.checkStatus() } coAnswers {
            if (calls++ == 0) old.await() else VoiceModelStatus(VoiceModelState.READY)
        }
        val vm = VoiceModelViewModel(manager)
        runCurrent(); vm.refresh(); runCurrent()
        assertEquals(1, calls)
        old.complete(VoiceModelStatus(VoiceModelState.NOT_INSTALLED))
        runCurrent()
        assertEquals(VoiceModelUiState.Ready, vm.uiState.value)
        assertEquals(2, calls)
    }

    @Test fun `initial resume never repeats a completed initialization query`() = runTest(dispatcher) {
        coEvery { manager.checkStatus() } returns VoiceModelStatus(VoiceModelState.READY)
        val vm = VoiceModelViewModel(manager)
        runCurrent(); vm.onResume(); runCurrent()
        coVerify(exactly = 1) { manager.checkStatus() }
        coEvery { manager.checkStatus() } returns VoiceModelStatus(VoiceModelState.NOT_INSTALLED)
        vm.onResume(); runCurrent()
        coVerify(exactly = 2) { manager.checkStatus() }
        assertEquals(VoiceModelState.NOT_INSTALLED, (vm.uiState.value as VoiceModelUiState.NeedsModel).state)
    }

    @Test fun `initial resume joins an in-flight initialization query`() = runTest(dispatcher) {
        val result = CompletableDeferred<VoiceModelStatus>()
        coEvery { manager.checkStatus() } coAnswers { result.await() }
        val vm = VoiceModelViewModel(manager)
        runCurrent(); vm.onResume(); runCurrent()
        result.complete(VoiceModelStatus(VoiceModelState.READY)); runCurrent()
        coVerify(exactly = 1) { manager.checkStatus() }
        assertEquals(VoiceModelUiState.Ready, vm.uiState.value)
    }
}
