/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.SessionRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionUseCasesTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var selectProfileUseCase: SelectProfileUseCase
    private lateinit var logoutUseCase: LogoutUseCase

    @BeforeEach
    fun setUp() {
        sessionRepository = mockk()
        getCurrentUserUseCase = GetCurrentUserUseCase(sessionRepository)
        selectProfileUseCase = SelectProfileUseCase(sessionRepository)
        logoutUseCase = LogoutUseCase(sessionRepository)
    }

    @Test
    fun `should_returnCurrentUser_when_userIsLoggedIn`() = runTest {
        // Given
        val user = UserProfile(id = 1, name = "Arjun", profileType = ProfileType.INDIVIDUAL, avatarIndex = 0)
        val userFlow = MutableStateFlow<UserProfile?>(user)
        every { sessionRepository.currentUser } returns userFlow

        // When
        val result = getCurrentUserUseCase().first()

        // Then
        assertEquals(user, result)
    }

    @Test
    fun `should_loginUser_when_selectProfileIsCalled`() = runTest {
        // Given
        val user = UserProfile(id = 1, name = "Arjun", profileType = ProfileType.INDIVIDUAL, avatarIndex = 0)
        every { sessionRepository.login(user) } just runs

        // When
        selectProfileUseCase(user)

        // Then
        verify(exactly = 1) { sessionRepository.login(user) }
    }

    @Test
    fun `should_logoutUser_when_logoutIsCalled`() = runTest {
        // Given
        every { sessionRepository.logout() } just runs

        // When
        logoutUseCase()

        // Then
        verify(exactly = 1) { sessionRepository.logout() }
    }
}
