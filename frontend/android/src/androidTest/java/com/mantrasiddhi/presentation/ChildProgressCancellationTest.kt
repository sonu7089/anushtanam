package com.mantrasiddhi.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.notification.GoalNotifier
import com.mantrasiddhi.domain.mantra.BundledMantraCatalog
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.*
import com.mantrasiddhi.domain.usecase.*
import com.mantrasiddhi.presentation.screens.childprogress.*
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

/** Real use cases, Android Main and Compose; all records are disposable in-memory fixtures. */
class ChildProgressCancellationTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val parent = UserProfile(101, "Parent", ProfileType.PARENT, avatarIndex = 0, firebaseUid = "fixture-parent")
    private val children = listOf(
        UserProfile(102, "Fixture A", ProfileType.CHILD, parent.id, 1),
        UserProfile(103, "Fixture B", ProfileType.CHILD, parent.id, 2)
    )
    private val account = MutableStateFlow<UserProfile?>(parent)
    private val visible = mutableStateOf(true)
    private val sessions = children.associate { it.id to MutableStateFlow(emptyList<RecitationSession>()) }
    private val active = children.associate { it.id to AtomicInteger() }
    private lateinit var viewModel: ChildProgressViewModel
    private lateinit var activity: ComponentActivity

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java)
    ) { _, method, _ -> error("Unexpected repository call: ${method.name}") } as T

    private fun start() {
        val session = object : SessionRepository by unused<SessionRepository>() {
            override val currentUser = account
        }
        val users = object : UserRepository by unused<UserRepository>() {
            override fun getChildrenOf(parentId: Long) = flowOf(children.filter { it.parentId == parentId })
            override suspend fun getById(id: Long) = children.firstOrNull { it.id == id }
        }
        val targets = object : TargetRepository by unused<TargetRepository>() {
            override fun getAllForUser(userId: Long): Flow<List<Target>> = flowOf(emptyList())
        }
        val recitations = object : RecitationRepository by unused<RecitationRepository>() {
            override fun getSessionsForUser(userId: Long): Flow<List<RecitationSession>> = flow {
                active.getValue(userId).incrementAndGet()
                try { emitAll(sessions.getValue(userId)) }
                finally { active.getValue(userId).decrementAndGet() }
            }
        }
        compose.runOnUiThread {
            viewModel = ChildProgressViewModel(
                GetCurrentUserUseCase(session), GetChildrenUseCase(users),
                GetChildProgressUseCase(users, targets, recitations),
                GetChildWeakWordsUseCase(recitations, BundledMantraCatalog.default),
                CreateTargetUseCase(targets, unused<TargetSyncRepository>()),
                GoalNotifier(InstrumentationRegistry.getInstrumentation().targetContext)
            )
            store.put("fixture", viewModel)
        }
        compose.setContent {
            activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
            androidx.compose.runtime.DisposableEffect(activity) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            AnushtanamTheme(false) {
                if (visible.value) ChildProgressScreen(viewModel = viewModel)
            }
        }
        compose.waitUntil(5_000) { viewModel.uiState.value is ChildProgressUiState.Success }
    }

    private fun select(index: Int) {
        compose.runOnIdle { viewModel.selectChild(children[index].id) }
        compose.waitUntil(5_000) {
            (viewModel.detailUiState.value as? ChildDetailUiState.Success)?.childProgress?.child?.id == children[index].id
        }
        compose.onNodeWithText("${children[index].name}'s Profile").assertIsDisplayed()
        assertSubscriptions(if (index == 0) 3 else 1, if (index == 1) 3 else 1)
    }

    private fun assertSubscriptions(a: Int, b: Int) {
        compose.waitUntil(5_000) {
            active.getValue(children[0].id).get() == a && active.getValue(children[1].id).get() == b
        }
    }

    @After fun cleanup() {
        compose.runOnUiThread { store.clear() }
        assertSubscriptions(0, 0)
    }

    @Test fun repeatedSelectionAndToolbarCloseReleaseDetailSubscriptions() {
        start()
        select(0)
        select(1)
        select(0)
        // The foreground detail toolbar precedes the covered overview in traversal order.
        compose.onAllNodesWithContentDescription("Back")[0].performClick()
        compose.runOnIdle { assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value) }
        assertSubscriptions(1, 1)
        compose.runOnIdle {
            children.forEach { child ->
                sessions.getValue(child.id).value = listOf(RecitationSession(
                    userId = child.id, mantraId = BundledMantraCatalog.default.id,
                    mode = RecitationMode.NORMAL, overallScore = 1.0, wordScores = emptyMap(),
                    transcription = "fixture update", isCorrect = true, durationMs = 1000
                ))
            }
        }
        compose.runOnIdle { assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value) }
    }

    @Test fun systemBackAndScreenDisposalCancelDetailsWithRetainedViewModel() {
        start()
        select(0)
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        assertSubscriptions(1, 1)
        compose.runOnIdle { assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value) }
        select(1)
        compose.runOnIdle { visible.value = false }
        assertSubscriptions(0, 0)
        compose.runOnIdle { assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value) }
    }

    @Test fun accountReplacementClosesDetailsAndDoesNotReopenOnReturn() {
        start()
        select(0)
        compose.runOnIdle { account.value = parent.copy(firebaseUid = "other-fixture-account") }
        assertSubscriptions(1, 1)
        compose.runOnIdle { assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value) }
        compose.runOnIdle { account.value = parent }
        assertSubscriptions(1, 1)
        compose.runOnIdle { assertEquals(ChildDetailUiState.Idle, viewModel.detailUiState.value) }
    }
}
