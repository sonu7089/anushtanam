/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.targets

import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.usecase.AbandonTargetUseCase
import com.mantrasiddhi.domain.usecase.CreateTargetUseCase
import com.mantrasiddhi.domain.usecase.GetChildrenUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TargetsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var createTargetUseCase: CreateTargetUseCase
    private lateinit var abandonTargetUseCase: AbandonTargetUseCase
    private lateinit var getChildrenUseCase: GetChildrenUseCase
    private lateinit var targetRepository: TargetRepository

    private lateinit var viewModel: TargetsViewModel

    private val testUser = UserProfile(
        id = 1L,
        name = "Meera",
        profileType = ProfileType.PARENT,
        parentId = null,
        avatarIndex = 1
    )

    private val testChild = UserProfile(
        id = 2L,
        name = "Rahul",
        profileType = ProfileType.CHILD,
        parentId = 1L,
        avatarIndex = 2
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getCurrentUserUseCase = mockk()
        createTargetUseCase = mockk()
        abandonTargetUseCase = mockk()
        getChildrenUseCase = mockk()
        targetRepository = mockk()

        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(testUser)
        coEvery { targetRepository.getAllForUser(testUser.id) } returns flowOf(emptyList())
        coEvery { getChildrenUseCase(testUser.id) } returns flowOf(listOf(testChild))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should_emitSuccessStateWithTargetsAndChildren_when_parentProfileSelected`() = runTest(testDispatcher) {
        val activeTarget = Target(id = 1L, userId = 1L, setByUserId = 1L, targetRecitationCount = 108, currentRecitationCount = 50, moneyAmount = 10.0, isCompleted = false)
        val completedTarget = Target(id = 2L, userId = 1L, setByUserId = 1L, targetRecitationCount = 108, currentRecitationCount = 108, moneyAmount = 10.0, isCompleted = true)

        coEvery { targetRepository.getAllForUser(testUser.id) } returns flowOf(listOf(activeTarget, completedTarget))

        viewModel = TargetsViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            createTargetUseCase = createTargetUseCase,
            abandonTargetUseCase = abandonTargetUseCase,
            getChildrenUseCase = getChildrenUseCase,
            targetRepository = targetRepository
        )

        val state = viewModel.uiState.value
        assertTrue(state is TargetsUiState.Success)
        val successState = state as TargetsUiState.Success
        assertEquals(testUser, successState.currentUser)
        assertEquals(activeTarget, successState.activeTarget)
        assertEquals(1, successState.completedTargets.size)
        assertEquals(completedTarget, successState.completedTargets[0])
        assertEquals(1, successState.children.size)
        assertEquals(testChild, successState.children[0])
    }

    @Test
    fun `should_emitErrorState_when_noUserSelected`() = runTest(testDispatcher) {
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(null)

        viewModel = TargetsViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            createTargetUseCase = createTargetUseCase,
            abandonTargetUseCase = abandonTargetUseCase,
            getChildrenUseCase = getChildrenUseCase,
            targetRepository = targetRepository
        )

        val state = viewModel.uiState.value
        assertTrue(state is TargetsUiState.Error)
        assertEquals("No active user profile selected.", (state as TargetsUiState.Error).message)
    }

    @Test
    fun `should_triggerCreateTargetUseCaseAndEmitEvent_when_createTargetCalledSuccessfully`() = runTest(testDispatcher) {
        coEvery { createTargetUseCase(any()) } returns Result.success(10L)

        viewModel = TargetsViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            createTargetUseCase = createTargetUseCase,
            abandonTargetUseCase = abandonTargetUseCase,
            getChildrenUseCase = getChildrenUseCase,
            targetRepository = targetRepository
        )
        runCurrent()

        val events = mutableListOf<TargetsEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.createTarget(108, 50.0, null)
        runCurrent()

        assertTrue(events.contains(TargetsEvent.TargetCreated))
        coVerify(exactly = 1) { createTargetUseCase(any()) }
        job.cancel()
    }

    @Test
    fun `should_emitErrorEvent_when_createTargetUseCaseFails`() = runTest(testDispatcher) {
        coEvery { createTargetUseCase(any()) } returns Result.failure(IllegalStateException("Already has active target"))

        viewModel = TargetsViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            createTargetUseCase = createTargetUseCase,
            abandonTargetUseCase = abandonTargetUseCase,
            getChildrenUseCase = getChildrenUseCase,
            targetRepository = targetRepository
        )
        runCurrent()

        val events = mutableListOf<TargetsEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.createTarget(108, 50.0, null)
        runCurrent()

        assertTrue(events.any { it is TargetsEvent.ShowError && it.message == "Already has active target" })
        job.cancel()
    }

    @Test
    fun `should_triggerAbandonTargetUseCaseAndEmitEvent_when_abandonTargetCalledSuccessfully`() = runTest(testDispatcher) {
        coEvery { abandonTargetUseCase(testUser.id) } returns true

        viewModel = TargetsViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            createTargetUseCase = createTargetUseCase,
            abandonTargetUseCase = abandonTargetUseCase,
            getChildrenUseCase = getChildrenUseCase,
            targetRepository = targetRepository
        )
        runCurrent()

        val events = mutableListOf<TargetsEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.abandonTarget(null)
        runCurrent()

        assertTrue(events.contains(TargetsEvent.TargetAbandoned))
        coVerify(exactly = 1) { abandonTargetUseCase(testUser.id) }
        job.cancel()
    }
}
