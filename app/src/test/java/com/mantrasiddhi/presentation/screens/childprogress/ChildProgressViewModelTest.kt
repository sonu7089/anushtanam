/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.childprogress

import com.mantrasiddhi.data.notification.GoalNotifier
import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.CreateTargetUseCase
import com.mantrasiddhi.domain.usecase.GetChildProgressUseCase
import com.mantrasiddhi.domain.usecase.GetChildWeakWordsUseCase
import com.mantrasiddhi.domain.usecase.GetChildrenUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class ChildProgressViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var getChildrenUseCase: GetChildrenUseCase
    private lateinit var getChildProgressUseCase: GetChildProgressUseCase
    private lateinit var getChildWeakWordsUseCase: GetChildWeakWordsUseCase
    private lateinit var createTargetUseCase: CreateTargetUseCase
    private lateinit var goalNotifier: GoalNotifier

    private lateinit var viewModel: ChildProgressViewModel

    private val parentUser = UserProfile(
        id = 1L,
        name = "Meera",
        profileType = ProfileType.PARENT,
        parentId = null,
        avatarIndex = 1
    )

    private val childUser = UserProfile(
        id = 2L,
        name = "Rahul",
        profileType = ProfileType.CHILD,
        parentId = 1L,
        avatarIndex = 2
    )

    private val individualUser = UserProfile(
        id = 3L,
        name = "Karan",
        profileType = ProfileType.INDIVIDUAL,
        parentId = null,
        avatarIndex = 3
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getCurrentUserUseCase = mockk()
        getChildrenUseCase = mockk()
        getChildProgressUseCase = mockk()
        getChildWeakWordsUseCase = mockk()
        createTargetUseCase = mockk()
        goalNotifier = mockk(relaxed = true)

        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(parentUser)
        coEvery { getChildrenUseCase(parentUser.id) } returns flowOf(listOf(childUser))

        val progress = ChildProgress(
            child = childUser,
            activeTarget = null,
            totalRecitations = 120,
            completedTargetCount = 3,
            recentSessions = emptyList()
        )
        coEvery { getChildProgressUseCase(childUser.id) } returns flowOf(progress)
        coEvery { getChildWeakWordsUseCase(childUser.id) } returns flowOf(listOf("धीमहि"))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should_emitSuccessWithChildrenProgress_when_parentProfileActive`() = runTest(testDispatcher) {
        viewModel = ChildProgressViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            getChildrenUseCase = getChildrenUseCase,
            getChildProgressUseCase = getChildProgressUseCase,
            getChildWeakWordsUseCase = getChildWeakWordsUseCase,
            createTargetUseCase = createTargetUseCase,
            goalNotifier = goalNotifier
        )

        val state = viewModel.uiState.value
        assertTrue(state is ChildProgressUiState.Success)
        val success = state as ChildProgressUiState.Success
        assertEquals(parentUser, success.parentUser)
        assertEquals(1, success.childrenProgress.size)
        assertEquals(childUser.id, success.childrenProgress[0].child.id)
    }

    @Test
    fun `should_emitErrorState_when_profileIsIndividual`() = runTest(testDispatcher) {
        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(individualUser)

        viewModel = ChildProgressViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            getChildrenUseCase = getChildrenUseCase,
            getChildProgressUseCase = getChildProgressUseCase,
            getChildWeakWordsUseCase = getChildWeakWordsUseCase,
            createTargetUseCase = createTargetUseCase,
            goalNotifier = goalNotifier
        )

        val state = viewModel.uiState.value
        assertTrue(state is ChildProgressUiState.Error)
        assertEquals("Access Denied: Only parent profiles can view child progress.", (state as ChildProgressUiState.Error).message)
    }

    @Test
    fun `should_loadChildDetailAndWeakWords_when_childSelected`() = runTest(testDispatcher) {
        viewModel = ChildProgressViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            getChildrenUseCase = getChildrenUseCase,
            getChildProgressUseCase = getChildProgressUseCase,
            getChildWeakWordsUseCase = getChildWeakWordsUseCase,
            createTargetUseCase = createTargetUseCase,
            goalNotifier = goalNotifier
        )

        assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value)

        viewModel.selectChild(childUser.id)

        val state = viewModel.detailUiState.value
        assertTrue(state is ChildDetailUiState.Success)
        val success = state as ChildDetailUiState.Success
        assertEquals(childUser.id, success.childProgress.child.id)
        assertEquals(1, success.weakWords.size)
        assertEquals("धीमहि", success.weakWords[0])
    }

    @Test
    fun `should_clearSelectedChild_when_clearSelectedChildCalled`() = runTest(testDispatcher) {
        viewModel = ChildProgressViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            getChildrenUseCase = getChildrenUseCase,
            getChildProgressUseCase = getChildProgressUseCase,
            getChildWeakWordsUseCase = getChildWeakWordsUseCase,
            createTargetUseCase = createTargetUseCase,
            goalNotifier = goalNotifier
        )

        viewModel.selectChild(childUser.id)
        assertTrue(viewModel.detailUiState.value is ChildDetailUiState.Success)

        viewModel.clearSelectedChild()
        assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value)
    }

    @Test
    fun `should_triggerCreateTargetUseCaseForChild_when_setChildTargetCalledSuccessfully`() = runTest(testDispatcher) {
        coEvery { createTargetUseCase(any()) } returns Result.success(45L)

        viewModel = ChildProgressViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            getChildrenUseCase = getChildrenUseCase,
            getChildProgressUseCase = getChildProgressUseCase,
            getChildWeakWordsUseCase = getChildWeakWordsUseCase,
            createTargetUseCase = createTargetUseCase,
            goalNotifier = goalNotifier
        )
        runCurrent()

        val events = mutableListOf<ChildProgressEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.setChildTarget(childUser.id, 1008, RewardType.GIFT, "New cricket bat")
        runCurrent()

        assertTrue(events.contains(ChildProgressEvent.TargetSetSuccess))
        coVerify(exactly = 1) { createTargetUseCase(any()) }
        // Parent-for-child goal must post the celebratory notification.
        verify(exactly = 1) { goalNotifier.showGoalSet(1008, RewardType.GIFT, "New cricket bat") }
        job.cancel()
    }
}
