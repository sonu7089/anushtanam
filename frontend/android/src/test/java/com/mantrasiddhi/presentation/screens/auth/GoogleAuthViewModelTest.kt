package com.mantrasiddhi.presentation.screens.auth

import android.content.Context
import com.mantrasiddhi.data.session.SyncSignedInUserUseCase
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.AuthRepository
import com.mantrasiddhi.resources.R
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleAuthViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val auth = mockk<AuthRepository>()
    private val picker = mockk<GoogleCredentialPicker>()
    private val profiles = mockk<SyncSignedInUserUseCase>()
    private val context = mockk<Context>()
    private val adult = AuthAccount("same-uid", AccountRole.INDIVIDUAL, "Meera", email = "meera@example.com", googleLinked = true)
    private lateinit var model: GoogleAuthViewModel

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { auth.currentAccount } returns flowOf(null)
        coEvery { picker.pick(context) } returns "test-token"
        coEvery { profiles.profilesForImport(any()) } returns emptyList()
        coEvery { profiles(any()) } returns 42L
        model = GoogleAuthViewModel(auth, picker, profiles)
    }
    @AfterEach fun teardown() { Dispatchers.resetMain() }

    @Test fun `cancelling account picker leaves the form ready without authenticating`() = runTest(dispatcher) {
        coEvery { picker.pick(context) } returns null
        model.start(context)
        assertEquals(GoogleAuthUiState(), model.state.value)
        coVerify(exactly = 0) { auth.signInWithGoogle(any(), any()) }
    }

    @Test fun `Google defaults to individual practice and opens the same returned account`() = runTest(dispatcher) {
        coEvery { auth.signInWithGoogle("test-token", AccountRole.INDIVIDUAL) } returns Result.success(adult)
        model.start(context)
        coVerify { profiles(adult) }
        assertTrue(model.state.value.completed)
    }

    @Test fun `link collision never signs into another account or changes local history`() = runTest(dispatcher) {
        coEvery { auth.linkGoogle("test-token", adult.uid) } returns Result.failure(GoogleAuthFailure(GoogleAuthProblem.COLLISION))
        model.start(context, linkTo = adult)
        assertEquals(R.string.google_link_collision, model.state.value.errorRes)
        coVerify(exactly = 0) { auth.signInWithGoogle(any(), any()); profiles(any()) }
    }

    @Test fun `successful link keeps the authenticated uid and skips local import`() = runTest(dispatcher) {
        coEvery { auth.linkGoogle("test-token", adult.uid) } returns Result.success(adult)
        model.start(context, linkTo = adult)
        assertTrue(model.state.value.linked)
        coVerify { profiles(adult) }
        coVerify(exactly = 0) { profiles.profilesForImport(any()) }
    }

    @Test fun `profile failure retries without requesting or exchanging another credential`() = runTest(dispatcher) {
        coEvery { auth.signInWithGoogle(any(), any()) } returns Result.success(adult)
        coEvery { profiles(adult) } throws IllegalStateException("disk unavailable")
        model.start(context)
        assertTrue(model.state.value.needsProfileRetry)
        assertFalse(model.state.value.completed)
        coEvery { profiles(adult) } returns 42L
        model.retryProfile()
        assertTrue(model.state.value.completed)
        coVerify(exactly = 1) { picker.pick(context); auth.signInWithGoogle(any(), any()) }
    }

    @Test fun `saved local profile is connected only after choosing it for the shown account`() = runTest(dispatcher) {
        val local = UserProfile(7, "My chosen name", ProfileType.INDIVIDUAL, avatarIndex = 2)
        coEvery { auth.signInWithGoogle(any(), any()) } returns Result.success(adult)
        coEvery { profiles.profilesForImport(adult) } returns listOf(local)
        coEvery { profiles.importProfile(adult, 7) } returns 7L
        model.start(context)
        assertEquals(adult.email, model.state.value.account?.email)
        assertEquals(listOf(local), model.state.value.importChoices)
        coVerify(exactly = 0) { profiles(any()); profiles.importProfile(any(), any()) }
        model.continueWithProfile(99)
        coVerify(exactly = 0) { profiles.importProfile(any(), any()) }
        model.continueWithProfile(7)
        coVerify(exactly = 1) { profiles.importProfile(adult, 7) }
        assertTrue(model.state.value.completed)
    }

    @Test fun `offline failure gives a useful message and does not change profiles`() = runTest(dispatcher) {
        coEvery { picker.pick(context) } throws GooglePickerFailure(R.string.google_offline)
        model.start(context)
        assertEquals(R.string.google_offline, model.state.value.errorRes)
        assertFalse(model.state.value.busy)
        coVerify(exactly = 0) { profiles(any()) }
    }

    @Test fun `new Google child sets a PIN before any local profile is opened`() = runTest(dispatcher) {
        val child = AuthAccount("child", AccountRole.CHILD, "Aarav", linkCode = "123456789012",
            googleLinked = true, needsChildPinSetup = true)
        coEvery { auth.signInWithGoogle("test-token", AccountRole.CHILD) } returns Result.success(child)
        model.start(context, AccountRole.CHILD)
        assertTrue(model.state.value.needsChildPin)
        assertFalse(model.state.value.completed)
        coVerify(exactly = 0) { profiles(any()); profiles.profilesForImport(any()) }
        model.saveChildPin("123456", "654321")
        assertEquals(R.string.child_pin_mismatch, model.state.value.errorRes)
        coVerify(exactly = 0) { auth.completeChildGoogleSetup(any(), any()) }
        val ready = child.copy(needsChildPinSetup = false)
        coEvery { auth.completeChildGoogleSetup("123456", child.uid) } returns Result.success(ready)
        model.saveChildPin("123456", "123456")
        assertTrue(model.state.value.completed)
        coVerify(exactly = 1) { profiles(ready) }
        coVerify(exactly = 0) { profiles.profilesForImport(any()) }
    }

    @Test fun `interrupted child setup resumes without choosing Google again`() = runTest(dispatcher) {
        val child = AuthAccount("child", AccountRole.CHILD, "Aarav", linkCode = "123456789012", needsChildPinSetup = true)
        model.resumeChildSetup(child)
        coEvery { auth.completeChildGoogleSetup("123456", child.uid) } returns Result.failure(GoogleAuthFailure(GoogleAuthProblem.OFFLINE))
        model.saveChildPin("123456", "123456")
        assertTrue(model.state.value.needsChildPin)
        assertFalse(model.state.value.busy)
        assertEquals(R.string.google_offline, model.state.value.errorRes)
        coVerify(exactly = 0) { picker.pick(any()); auth.signInWithGoogle(any(), any()); profiles(any()) }
    }

    @Test fun `adult Google account cannot open a child profile`() = runTest(dispatcher) {
        coEvery { auth.signInWithGoogle(any(), AccountRole.CHILD) } returns Result.failure(GoogleAuthFailure(GoogleAuthProblem.ROLE_MISMATCH))
        model.start(context, AccountRole.CHILD)
        assertEquals(R.string.google_role_mismatch, model.state.value.errorRes)
        coVerify(exactly = 0) { profiles(any()) }
    }

    @Test fun `existing child links Google to the same profile and never imports adult practice`() = runTest(dispatcher) {
        val child = AuthAccount("child", AccountRole.CHILD, "Aarav", linkCode = "123456789012", googleLinked = true)
        coEvery { auth.linkGoogle("test-token", child.uid) } returns Result.success(child)
        model.start(context, linkTo = child)
        assertTrue(model.state.value.linked)
        coVerify(exactly = 1) { profiles(child) }
        coVerify(exactly = 0) { auth.signInWithGoogle(any(), any()); profiles.profilesForImport(any()) }
    }
}
