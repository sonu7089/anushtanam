package com.mantrasiddhi.presentation

import androidx.lifecycle.ViewModelStore
import com.mantrasiddhi.data.notification.GoalNotifier
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.data.session.SyncSignedInUserUseCase
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.*
import com.mantrasiddhi.domain.usecase.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val resolution = MutableStateFlow<AccountResolution>(AccountResolution.Unresolved())
    private val auth = mockk<AuthRepository>()
    private val session = mockk<SessionRepository>(relaxed = true)
    private val bridge = mockk<SyncSignedInUserUseCase>()
    private val notifier = mockk<GoalNotifier>(relaxed = true)
    private val goals = mockk<ObserveChildGoalsUseCase>()
    private val store = ViewModelStore()
    private lateinit var model: MainViewModel
    private fun child(uid: String = "A") = AuthAccount(uid, AccountRole.CHILD, uid)
    private fun goal(id: String, uid: String = "A") = CloudGoal(id, "P", uid, RewardType.OTHER, id, 10, 0, false)

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { auth.accountResolution } returns resolution
        every { auth.currentAccount } returns resolution.map { (it as? AccountResolution.Resolved)?.account }.distinctUntilChanged()
        every { session.currentUser } returns MutableStateFlow(null)
        coEvery { bridge(any()) } returns 1L
        every { goals(any()) } returns flowOf(emptyList())
        model = MainViewModel(GetCurrentUserUseCase(session),
            mockk<UserPreferencesDataStore> { every { themeModeFlow } returns flowOf("SYSTEM") },
            ObserveAuthAccountUseCase(auth), goals, bridge, notifier, session, mockk(), auth)
        store.put("main", model)
    }
    @AfterEach fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun `startup waits for resolution and preserves recovery and child setup`() = runTest(dispatcher) {
        runCurrent()
        assertEquals(AuthGateState.Loading, model.authGateState.value)
        coVerify(exactly = 0) { session.logout() }
        resolution.value = AccountResolution.Unresolved("A")
        runCurrent()
        assertEquals(AuthGateState.Loading, model.authGateState.value)
        resolution.value = AccountResolution.Resolved(child().copy(googleLinked = true, profileNeedsSync = true))
        runCurrent()
        assertEquals(AuthGateState.NeedsRecovery, model.authGateState.value)
        resolution.value = AccountResolution.Resolved(child().copy(googleLinked = true, needsChildPinSetup = true))
        runCurrent()
        assertEquals(AuthGateState.NeedsChildSetup, model.authGateState.value)
        coVerify(exactly = 0) { bridge(any()) }
        resolution.value = AccountResolution.Resolved(child())
        runCurrent()
        assertEquals(AuthGateState.Authenticated, model.authGateState.value)
    }

    @Test fun `metadata refresh does not reopen the startup profile`() = runTest(dispatcher) {
        resolution.value = AccountResolution.Resolved(child())
        runCurrent()
        resolution.value = AccountResolution.Resolved(child().copy(displayName = "Renamed", photoUrl = "photo"))
        runCurrent()
        coVerify(exactly = 1) { bridge(any()) }
    }

    @Test fun `signed out startup leaves interactive profile import to its auth screen`() = runTest(dispatcher) {
        resolution.value = AccountResolution.SignedOut
        runCurrent()
        assertEquals(AuthGateState.Unauthenticated, model.authGateState.value)
        resolution.value = AccountResolution.Unresolved("A")
        runCurrent()
        resolution.value = AccountResolution.Resolved(child())
        runCurrent()
        coVerify(exactly = 0) { bridge(any()) }
        assertEquals(AuthGateState.Unauthenticated, model.authGateState.value)
    }

    @Test fun `account replacement cancels startup work and clears the old profile`() = runTest(dispatcher) {
        var cancelled = false
        coEvery { bridge(match { it.uid == "A" }) } coAnswers {
            try { awaitCancellation() } finally { cancelled = true }
        }
        resolution.value = AccountResolution.Resolved(child())
        runCurrent()
        resolution.value = AccountResolution.Unresolved("B")
        runCurrent()
        assertTrue(cancelled)
        assertEquals(AuthGateState.Loading, model.authGateState.value)
        coVerify { session.logout() }
        resolution.value = AccountResolution.Resolved(child("B"))
        runCurrent()
        assertEquals(AuthGateState.Authenticated, model.authGateState.value)
        resolution.value = AccountResolution.SignedOut
        runCurrent()
        assertEquals(AuthGateState.Unauthenticated, model.authGateState.value)
    }

    @Test fun `retry result cannot reopen a signed out profile`() = runTest(dispatcher) {
        resolution.value = AccountResolution.Resolved(child().copy(googleLinked = true, profileNeedsSync = true))
        runCurrent()
        val pending = CompletableDeferred<Result<AuthAccount>>()
        coEvery { auth.refreshAccount() } coAnswers { pending.await() }
        model.retryOpeningProfile()
        runCurrent()
        resolution.value = AccountResolution.SignedOut
        runCurrent()
        pending.complete(Result.success(child()))
        runCurrent()
        assertEquals(AuthGateState.Unauthenticated, model.authGateState.value)
        coVerify(exactly = 0) { bridge(any()) }
    }

    @Test fun `retry queued across sign out cannot change the signed out gate`() = runTest(dispatcher) {
        resolution.value = AccountResolution.Resolved(child().copy(googleLinked = true, profileNeedsSync = true))
        runCurrent()
        coEvery { auth.refreshAccount() } returns Result.failure(IllegalStateException("signed out"))
        resolution.value = AccountResolution.SignedOut
        model.retryOpeningProfile()
        runCurrent()
        assertEquals(AuthGateState.Unauthenticated, model.authGateState.value)
    }

    @Test fun `refresh publication and retry response open the recovered profile once`() = runTest(dispatcher) {
        resolution.value = AccountResolution.Resolved(child().copy(googleLinked = true, profileNeedsSync = true))
        runCurrent()
        coEvery { auth.refreshAccount() } coAnswers {
            resolution.value = AccountResolution.Resolved(child())
            yield()
            Result.success(child())
        }
        model.retryOpeningProfile()
        runCurrent()
        assertEquals(AuthGateState.Authenticated, model.authGateState.value)
        coVerify(exactly = 1) { bridge(child()) }
    }

    @Test fun `notification baseline survives metadata refresh and resets for a different child`() = runTest(dispatcher) {
        val a = MutableStateFlow(listOf(goal("old")))
        val b = MutableStateFlow(listOf(goal("b-old", "B")))
        every { goals("A") } returns a
        every { goals("B") } returns b
        resolution.value = AccountResolution.Resolved(child())
        runCurrent()
        verify(exactly = 0) { notifier.showGoalSet(any(), any(), any()) }
        a.value += goal("new")
        runCurrent()
        verify(exactly = 1) { notifier.showGoalSet(10, RewardType.OTHER, "new") }
        resolution.value = AccountResolution.Resolved(child().copy(displayName = "Updated"))
        runCurrent()
        verify(exactly = 1) { goals("A") }
        a.value = listOf(goal("old"))
        runCurrent()
        a.value += goal("new")
        runCurrent()
        verify(exactly = 1) { notifier.showGoalSet(any(), any(), any()) }
        resolution.value = AccountResolution.Unresolved("B")
        runCurrent()
        resolution.value = AccountResolution.Resolved(child("B"))
        runCurrent()
        verify(exactly = 1) { notifier.showGoalSet(any(), any(), any()) }
        b.value += goal("b-new", "B")
        runCurrent()
        verify(exactly = 1) { notifier.showGoalSet(10, RewardType.OTHER, "b-new") }
        resolution.value = AccountResolution.SignedOut
        runCurrent()
        a.value += goal("away")
        resolution.value = AccountResolution.Resolved(child())
        runCurrent()
        verify(exactly = 2) { notifier.showGoalSet(any(), any(), any()) }
    }
}
