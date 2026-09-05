/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.auth

import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.domain.usecase.SendParentPasswordResetUseCase
import com.mantrasiddhi.domain.usecase.SignInChildUseCase
import com.mantrasiddhi.domain.usecase.SignInParentUseCase
import com.mantrasiddhi.domain.usecase.SignUpChildUseCase
import com.mantrasiddhi.domain.usecase.SignUpParentUseCase
import com.mantrasiddhi.data.session.SyncSignedInUserUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var signUpParentUseCase: SignUpParentUseCase
    private lateinit var signInParentUseCase: SignInParentUseCase
    private lateinit var signUpChildUseCase: SignUpChildUseCase
    private lateinit var signInChildUseCase: SignInChildUseCase
    private lateinit var sendParentPasswordResetUseCase: SendParentPasswordResetUseCase
    private lateinit var syncSignedInUserUseCase: SyncSignedInUserUseCase
    private lateinit var viewModel: AuthViewModel

    private val parentAccount = AuthAccount(uid = "p1", role = AccountRole.PARENT, displayName = "Meera")
    private val childAccount = AuthAccount(
        uid = "c1", role = AccountRole.CHILD, displayName = "Aarav", linkCode = "123456789012"
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        signUpParentUseCase = mockk()
        signInParentUseCase = mockk()
        signUpChildUseCase = mockk()
        signInChildUseCase = mockk()
        sendParentPasswordResetUseCase = mockk()
        syncSignedInUserUseCase = mockk(relaxed = true)
        viewModel = AuthViewModel(
            signUpParentUseCase,
            signInParentUseCase,
            signUpChildUseCase,
            signInChildUseCase,
            sendParentPasswordResetUseCase,
            syncSignedInUserUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `parent login success syncs profile and navigates home`() = runTest(testDispatcher) {
        coEvery { signInParentUseCase(any(), any()) } returns Result.success(parentAccount)
        val events = mutableListOf<AuthEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.signInParent("meera@example.com", "secret1")

        coVerify { syncSignedInUserUseCase(parentAccount) }
        assertTrue(events.any { it is AuthEvent.NavigateToHome })
        assertNull(viewModel.uiState.value.errorMessageResId)
        job.cancel()
    }

    @Test
    fun `parent login failure surfaces error and does not navigate`() = runTest(testDispatcher) {
        coEvery { signInParentUseCase(any(), any()) } returns Result.failure(RuntimeException("bad creds"))
        val events = mutableListOf<AuthEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.signInParent("meera@example.com", "wrong")

        assertEquals("bad creds", viewModel.uiState.value.errorDetail)
        assertTrue(events.none { it is AuthEvent.NavigateToHome })
        job.cancel()
    }

    @Test
    fun `parent login validates blank input without calling backend`() = runTest(testDispatcher) {
        viewModel.signInParent("", "")
        assertEquals(
            R.string.auth_error_missing_credentials,
            viewModel.uiState.value.errorMessageResId
        )
        coVerify(exactly = 0) { signInParentUseCase(any(), any()) }
    }

    @Test
    fun `child signup reveals generated id and defers navigation`() = runTest(testDispatcher) {
        coEvery { signUpChildUseCase(any(), any()) } returns Result.success(childAccount)
        val events = mutableListOf<AuthEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.signUpChild("Aarav", "123456")

        assertEquals("123456789012", viewModel.uiState.value.generatedChildId)
        assertTrue(events.none { it is AuthEvent.NavigateToHome })

        // Then continue proceeds into the app.
        viewModel.continueAfterChildSignup()
        coVerify { syncSignedInUserUseCase(childAccount) }
        assertTrue(events.any { it is AuthEvent.NavigateToHome })
        job.cancel()
    }

    @Test
    fun `child login delegates to backend and navigates on success`() = runTest(testDispatcher) {
        coEvery { signInChildUseCase("123456789012", "123456") } returns Result.success(childAccount)
        val events = mutableListOf<AuthEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.signInChild("123456789012", "123456")

        coVerify { syncSignedInUserUseCase(childAccount) }
        assertTrue(events.any { it is AuthEvent.NavigateToHome })
        job.cancel()
    }
}
