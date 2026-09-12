package com.mantrasiddhi.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.*
import com.mantrasiddhi.domain.usecase.*
import com.mantrasiddhi.presentation.screens.progress.*
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

class ProgressDemandUiTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val active = AtomicInteger()
    private val historyStarts = AtomicInteger()
    private val scoreStarts = AtomicInteger()
    private lateinit var lifecycle: androidx.lifecycle.LifecycleRegistry
    private lateinit var vm: ProgressViewModel
    private val visible = mutableStateOf(true)
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> error("Unexpected ${method.name}") } as T
    private fun <T> source(value: T): Flow<T> = flow {
        active.incrementAndGet()
        try { emit(value); awaitCancellation() } finally { active.decrementAndGet() }
    }
    private fun start(slowOverview: Boolean = false) {
        val account = object : SessionRepository by unused<SessionRepository>() {
            override val currentUser = MutableStateFlow<UserProfile?>(UserProfile(1, "Fixture", ProfileType.INDIVIDUAL, avatarIndex = 0))
        }
        val repository = object : RecitationRepository by unused<RecitationRepository>() {
            override fun observeTotals(userId: Long): Flow<SessionTotals> = if (slowOverview) flow { awaitCancellation() }
                else source(SessionTotals(80, 40, .8, .9))
            override fun observePracticeDays(userId: Long) = source(emptyList<PracticeDay>())
            override fun observeScoreDays(userId: Long, since: Long, until: Long): Flow<List<ScoreDay>> {
                scoreStarts.incrementAndGet(); return source(emptyList())
            }
            override fun observeHistoryPage(userId: Long, beforeTime: Long, beforeId: Long, limit: Int): Flow<List<SessionHistoryRow>> {
                historyStarts.incrementAndGet()
                return source((80L downTo 1).filter { it < beforeId }.take(limit)
                    .map { SessionHistoryRow(it, 100, "NORMAL", .8, true, 1000) })
            }
        }
        val cloud = object : ProgressSyncRepository by unused<ProgressSyncRepository>() {
            override fun summaryFlow() = source<ProgressSummary?>(null)
        }
        val japa = object : JapaHistoryRepository { override fun observe(userId: Long) = source(emptyList<PerformedJapa>()) }
        val owner = object : androidx.lifecycle.LifecycleOwner {
            override val lifecycle: androidx.lifecycle.Lifecycle get() = this@ProgressDemandUiTest.lifecycle
        }
        compose.runOnUiThread {
            lifecycle = androidx.lifecycle.LifecycleRegistry(owner).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
            vm = ProgressViewModel(GetCurrentUserUseCase(account), GetRecitationStatsUseCase(repository), repository, cloud, GetJapaSummaryUseCase(japa))
            store.put("progress", vm)
        }
        compose.setContent { CompositionLocalProvider(androidx.compose.ui.platform.LocalLifecycleOwner provides owner) { AnushtanamTheme(false) { if (visible.value) ProgressScreen(viewModel = vm) } } }
    }
    @After fun cleanup() { compose.runOnUiThread { store.clear() } }

    @Test fun selectedTabsPageAndReleaseAllSourcesWhenDestinationIsHidden() {
        start()
        compose.waitUntil(5000) { vm.uiState.value is ProgressUiState.Success }
        assertEquals(0, historyStarts.get()); assertEquals(0, scoreStarts.get())
        compose.runOnIdle { vm.selectTab(3) }
        compose.waitUntil(5000) { (vm.uiState.value as? ProgressUiState.Success)?.sessions?.size == 30 }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Load more"))
        compose.onNodeWithText("Load more").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { (vm.uiState.value as? ProgressUiState.Success)?.sessions?.size == 60 }
        assertEquals(2, historyStarts.get())
        compose.runOnIdle { vm.selectTab(2) }
        compose.waitUntil(5000) { scoreStarts.get() == 1 }
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(5000) { active.get() == 0 }
    }
    @Test fun slowOverviewLeavesTabControlsUsable() {
        start(slowOverview = true)
        compose.onNodeWithText("History", substring = false).assertIsDisplayed().performClick()
        compose.waitUntil(5000) { (vm.uiState.value as? ProgressUiState.Success)?.sessions?.size == 30 }
        assertEquals(1, historyStarts.get())
    }

    @Test fun stoppedLifecycleReleasesReadsAndResumeRestartsOnlySelectedTab() {
        start()
        compose.waitUntil(5000) { vm.uiState.value is ProgressUiState.Success }
        compose.runOnIdle { lifecycle.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        compose.waitUntil(5000) { active.get() == 0 }
        compose.runOnIdle { lifecycle.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.waitUntil(5000) { active.get() > 0 && vm.uiState.value is ProgressUiState.Success }
        assertEquals(0, historyStarts.get()); assertEquals(0, scoreStarts.get())
    }
}
