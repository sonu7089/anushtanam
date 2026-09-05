/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.settings

import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.SignOutUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var preferencesDataStore: UserPreferencesDataStore
    private lateinit var signOutUseCase: SignOutUseCase
    private lateinit var viewModel: SettingsViewModel

    private val testUser = UserProfile(
        id = 1L,
        name = "Meera",
        profileType = ProfileType.PARENT,
        parentId = null,
        avatarIndex = 1
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getCurrentUserUseCase = mockk()
        preferencesDataStore = mockk(relaxed = true)
        signOutUseCase = mockk(relaxed = true)

        coEvery { getCurrentUserUseCase() } returns MutableStateFlow(testUser)
        every { preferencesDataStore.themeModeFlow } returns flowOf("SYSTEM")
        every { preferencesDataStore.languageDisplayFlow } returns flowOf("BOTH")
    }


    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = SettingsViewModel(
            getCurrentUserUseCase,
            preferencesDataStore,
            signOutUseCase
        )
    }

    @Test
    fun `should_exposeCorrectInitialPreferences`() = runTest(testDispatcher) {
        createViewModel()
        assertEquals("SYSTEM", viewModel.themeMode.value)
        assertEquals("BOTH", viewModel.languageDisplay.value)
    }

    @Test
    fun `should_callSetThemeMode_when_setThemeModeCalled`() = runTest(testDispatcher) {
        createViewModel()
        viewModel.setThemeMode("DARK")
        coVerify { preferencesDataStore.setThemeMode("DARK") }
    }

    @Test
    fun `should_callSetLanguageDisplay_when_setLanguageDisplayCalled`() = runTest(testDispatcher) {
        createViewModel()
        viewModel.setLanguageDisplay("DEVANAGARI")
        coVerify { preferencesDataStore.setLanguageDisplay("DEVANAGARI") }
    }

    @Test
    fun `should_logoutAndEmitLoggedOutEvent_when_logoutCalled`() = runTest(testDispatcher) {
        createViewModel()
        val events = mutableListOf<SettingsEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.logout()

        coVerify { signOutUseCase() }
        assertEquals(1, events.size)
        assertTrue(events[0] is SettingsEvent.ProfileLoggedOut)
        job.cancel()
    }

}

