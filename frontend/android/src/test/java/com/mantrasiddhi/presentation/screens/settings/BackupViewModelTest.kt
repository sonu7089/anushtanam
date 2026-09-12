package com.mantrasiddhi.presentation.screens.settings

import androidx.lifecycle.ViewModelStore
import com.mantrasiddhi.data.local.dao.BackupReviewRow
import com.mantrasiddhi.data.local.entity.BackupStateEntity
import com.mantrasiddhi.data.sync.*
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val user = MutableStateFlow<UserProfile?>(UserProfile(7, "Fixture", ProfileType.INDIVIDUAL, avatarIndex = 0, firebaseUid = "owner"))
    private val observations = mockk<BackupObservation>()
    private lateinit var vm: BackupViewModel
    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { observations.state(any()) } returns flowOf(BackupStateEntity("owner", restorePending = false))
        every { observations.pending(any()) } returns flowOf(0)
        every { observations.quarantined(any()) } returns flowOf(0)
        every { observations.conflictCount(any()) } returns flowOf(50)
        vm = BackupViewModel(mockk<GetCurrentUserUseCase> { every { this@mockk.invoke() } returns user }, observations,
            mockk(), mockk<CloudSyncRequests> {
                every { activeUid } returns MutableStateFlow(null)
                every { manualReady(any()) } returns flowOf(true)
            })
    }
    @AfterEach fun cleanup() { ViewModelStore().apply { put("backup", vm); clear() }; Dispatchers.resetMain() }
    @Test fun `status never loads payloads and closing review cancels its source`() = runTest(dispatcher) {
        var active = 0
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        every { observations.review("owner", 7) } returns flow {
            active++; started.complete(Unit)
            try { emit(emptyList<BackupReviewRow>()); awaitCancellation() } finally { active--; stopped.complete(Unit) }
        }
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        assertEquals(50, vm.state.value.conflictCount)
        assertTrue(vm.state.value.attention)
        verify(exactly = 0) { observations.review(any(), any()) }
        vm.setReviewing(true)
        withContext(Dispatchers.Default) { started.await() }
        assertEquals(1, active)
        vm.setReviewing(false)
        withContext(Dispatchers.Default) { stopped.await() }
        assertEquals(0, active)
    }
}
