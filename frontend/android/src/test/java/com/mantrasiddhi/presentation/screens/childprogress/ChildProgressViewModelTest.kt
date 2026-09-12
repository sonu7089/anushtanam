/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.childprogress

import androidx.lifecycle.ViewModelStore
import com.mantrasiddhi.resources.R
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private val observers = mutableListOf<kotlinx.coroutines.Job>()
    private fun watch(vm: ChildProgressViewModel) {
        observers += kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { vm.uiState.collect {} }
        observers += kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { vm.detailUiState.collect {} }
    }

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
        if (::viewModel.isInitialized) {
            ViewModelStore().apply {
                put("child-progress", viewModel)
                clear()
            }
        }
        observers.forEach { it.cancel() }; Dispatchers.resetMain()
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
        watch(viewModel)

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
        watch(viewModel)

        val state = viewModel.uiState.value
        assertTrue(state is ChildProgressUiState.Error)
        assertEquals(R.string.child_error_parent_only, (state as ChildProgressUiState.Error).messageResId)
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
        watch(viewModel)

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
        watch(viewModel)

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
        watch(viewModel)
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

    private val secondChild = childUser.copy(id = 4L, name = "Anika")

    private fun progress(child: UserProfile) = ChildProgress(
        child = child, activeTarget = null, totalRecitations = 120,
        completedTargetCount = 3, recentSessions = emptyList()
    )

    private class TrackedFlow<T>(initial: T) {
        val values = MutableStateFlow(initial)
        var active = 0
        var starts = 0
        var peak = 0
        val flow: Flow<T> = flow {
            active++
            starts++
            peak = maxOf(peak, active)
            try {
                emitAll(values)
            } finally {
                active--
            }
        }
    }

    private inner class DetailFixture {
        val account = MutableStateFlow<UserProfile?>(parentUser)
        val progressA = TrackedFlow<ChildProgress?>(progress(childUser))
        val progressB = TrackedFlow<ChildProgress?>(progress(secondChild))
        val wordsA = TrackedFlow(listOf("A"))
        val wordsB = TrackedFlow(listOf("B"))

        init {
            coEvery { getCurrentUserUseCase() } returns account
            coEvery { getChildrenUseCase(any()) } returns flowOf(listOf(childUser, secondChild))
            coEvery { getChildProgressUseCase(childUser.id) } returns progressA.flow
            coEvery { getChildProgressUseCase(secondChild.id) } returns progressB.flow
            coEvery { getChildWeakWordsUseCase(childUser.id) } returns wordsA.flow
            coEvery { getChildWeakWordsUseCase(secondChild.id) } returns wordsB.flow
        }

        fun start() {
            viewModel = ChildProgressViewModel(
                getCurrentUserUseCase, getChildrenUseCase, getChildProgressUseCase,
                getChildWeakWordsUseCase, createTargetUseCase, goalNotifier
            )
        watch(viewModel)
        }

        fun assertActiveDetails(a: Int, b: Int) {
            // The overview retains one progress subscription per child (step 09).
            assertEquals(1 + a, progressA.active)
            assertEquals(1 + b, progressB.active)
            assertEquals(a, wordsA.active)
            assertEquals(b, wordsB.active)
        }

        fun assertChild(child: UserProfile, word: String) {
            val state = viewModel.detailUiState.value as ChildDetailUiState.Success
            assertEquals(child.id, state.childProgress.child.id)
            assertEquals(listOf(word), state.weakWords)
        }
    }

    @Test
    fun `A B A close cancels both detail sources and ignores subsequent updates`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = DetailFixture().apply { start() }
        runCurrent()
        fixture.assertActiveDetails(0, 0)
        for (child in listOf(childUser, secondChild, childUser)) {
            viewModel.selectChild(child.id)
            assertEquals(ChildDetailUiState.Loading, viewModel.detailUiState.value)
            runCurrent()
            val isA = child.id == childUser.id
            fixture.assertActiveDetails(if (isA) 1 else 0, if (isA) 0 else 1)
            fixture.assertChild(child, if (isA) "A" else "B")
            // Selecting the already visible child must not restart it.
            val starts = fixture.wordsA.starts + fixture.wordsB.starts
            viewModel.selectChild(child.id)
            runCurrent()
            assertEquals(starts, fixture.wordsA.starts + fixture.wordsB.starts)
        }
        fixture.wordsB.values.value = listOf("stale B")
        fixture.progressB.values.value = progress(secondChild).copy(totalRecitations = 999)
        runCurrent()
        fixture.assertChild(childUser, "A")
        viewModel.clearSelectedChild()
        assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value)
        fixture.wordsA.values.value = listOf("closed A")
        fixture.progressA.values.value = progress(childUser).copy(totalRecitations = 888)
        runCurrent()
        fixture.assertActiveDetails(0, 0)
        assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value)
        assertEquals(1, fixture.wordsA.peak)
        assertEquals(1, fixture.wordsB.peak)
        assertEquals(2, fixture.progressA.peak)
        assertEquals(2, fixture.progressB.peak)
    }

    @Test
    fun `rapid replacements and close before dispatch never reopen details`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = DetailFixture().apply { start() }
        runCurrent()
        viewModel.selectChild(childUser.id)
        runCurrent()
        fixture.wordsA.values.value = listOf("queued A")
        viewModel.selectChild(secondChild.id)
        viewModel.selectChild(childUser.id)
        viewModel.clearSelectedChild()
        runCurrent()
        fixture.assertActiveDetails(0, 0)
        assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value)
        viewModel.selectChild(secondChild.id)
        runCurrent()
        fixture.assertChild(secondChild, "B")
        fixture.assertActiveDetails(0, 1)
    }

    @Test
    fun `account switches including reused local ID cancel selection without automatic reopen`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = DetailFixture().apply { start() }
        runCurrent()
        for (replacement in listOf(
            parentUser.copy(id = 9L, firebaseUid = "other-parent"),
            parentUser.copy(firebaseUid = "different-account-same-local-id"),
            parentUser.copy(profileType = ProfileType.INDIVIDUAL),
            null
        )) {
            viewModel.selectChild(childUser.id)
            runCurrent()
            fixture.assertActiveDetails(1, 0)
            fixture.wordsA.values.value = listOf("queued before switch")
            fixture.account.value = replacement
            // A stale overview must not authorize another detail request.
            viewModel.selectChild(secondChild.id)
            runCurrent()
            assertEquals(0, fixture.wordsA.active)
            assertEquals(0, fixture.wordsB.active)
            assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value)
            fixture.account.value = parentUser
            runCurrent()
            fixture.assertActiveDetails(0, 0)
            assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value)
        }
    }

    @Test
    fun `same account profile edits keep the selected detail subscription`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = DetailFixture().apply { start() }
        runCurrent()
        viewModel.selectChild(childUser.id)
        runCurrent()
        fixture.account.value = parentUser.copy(name = "Updated name", avatarIndex = 4)
        runCurrent()
        fixture.assertChild(childUser, "A")
        fixture.assertActiveDetails(1, 0)
        assertEquals(1, fixture.wordsA.starts)
    }

    @Test
    fun `closing during loading cancels sources before first detail value`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = DetailFixture()
        var active = false
        coEvery { getChildWeakWordsUseCase(childUser.id) } returns flow {
            active = true
            try { kotlinx.coroutines.awaitCancellation() } finally { active = false }
        }
        fixture.start()
        runCurrent()
        viewModel.selectChild(childUser.id)
        runCurrent()
        assertTrue(active)
        assertEquals(ChildDetailUiState.Loading, viewModel.detailUiState.value)
        viewModel.clearSelectedChild()
        runCurrent()
        assertEquals(false, active)
        fixture.assertActiveDetails(0, 0)
        assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value)
    }

    @Test
    fun `detail errors cancel sibling work and allow retry or another child`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = DetailFixture()
        coEvery { getChildWeakWordsUseCase(childUser.id) } returns flow { error("source failed") }
        fixture.start()
        runCurrent()
        viewModel.selectChild(childUser.id)
        runCurrent()
        assertEquals(ChildDetailUiState.Error(R.string.child_error_load_details), viewModel.detailUiState.value)
        fixture.assertActiveDetails(0, 0)
        // Synchronous source construction failures are recoverable too.
        coEvery { getChildWeakWordsUseCase(childUser.id) } throws IllegalStateException("creation failed")
        viewModel.selectChild(childUser.id)
        runCurrent()
        assertEquals(ChildDetailUiState.Error(R.string.child_error_load_details), viewModel.detailUiState.value)
        coEvery { getChildWeakWordsUseCase(childUser.id) } returns fixture.wordsA.flow
        viewModel.selectChild(childUser.id)
        runCurrent()
        fixture.assertChild(childUser, "A")
        viewModel.selectChild(secondChild.id)
        runCurrent()
        fixture.assertChild(secondChild, "B")
        fixture.assertActiveDetails(0, 1)
    }

    @Test
    fun `missing or mismatched child results never become visible details`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = DetailFixture().apply { start() }
        runCurrent()
        viewModel.selectChild(999L)
        runCurrent()
        fixture.assertActiveDetails(0, 0)
        assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value)
        viewModel.selectChild(childUser.id)
        runCurrent()
        for (invalid in listOf(
            null, progress(secondChild), progress(childUser.copy(parentId = 99L)),
            progress(childUser.copy(profileType = ProfileType.INDIVIDUAL))
        )) {
            fixture.progressA.values.value = invalid
            runCurrent()
            assertEquals(ChildDetailUiState.Error(R.string.child_error_not_found), viewModel.detailUiState.value)
        }
    }

    @Test
    fun `account changes still cancel details after overview collection fails`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = DetailFixture()
        val children = MutableStateFlow(listOf(childUser, secondChild))
        coEvery { getChildrenUseCase(any()) } returns flow {
            children.collect {
                check(it.isNotEmpty()) { "overview failed" }
                emit(it)
            }
        }
        fixture.start()
        runCurrent()
        viewModel.selectChild(childUser.id)
        runCurrent()
        children.value = emptyList()
        runCurrent()
        assertEquals(ChildProgressUiState.Error(R.string.child_error_load_children), viewModel.uiState.value)
        assertEquals(1, fixture.wordsA.active)
        fixture.account.value = null
        runCurrent()
        assertEquals(0, fixture.progressA.active)
        assertEquals(0, fixture.wordsA.active)
        assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value)
        assertEquals(ChildProgressUiState.Error(R.string.child_error_no_parent_profile), viewModel.uiState.value)
    }

    @Test
    fun `clearing ViewModel releases overview and selected detail sources`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = DetailFixture().apply { start() }
        runCurrent()
        viewModel.selectChild(childUser.id)
        runCurrent()
        ViewModelStore().apply { put("child-progress", viewModel); clear() }
        runCurrent()
        assertEquals(0, fixture.progressA.active)
        assertEquals(0, fixture.progressB.active)
        assertEquals(0, fixture.wordsA.active)
        assertEquals(0, fixture.wordsB.active)
    }
}
