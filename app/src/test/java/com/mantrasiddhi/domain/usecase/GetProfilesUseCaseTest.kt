/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetProfilesUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var getProfilesUseCase: GetProfilesUseCase

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        getProfilesUseCase = GetProfilesUseCase(userRepository)
    }

    @Test
    fun `should_returnProfiles_when_profilesExist`() = runTest {
        // Given
        val profiles = listOf(
            UserProfile(id = 1, name = "Arjun", profileType = ProfileType.INDIVIDUAL, avatarIndex = 0),
            UserProfile(id = 2, name = "Meera", profileType = ProfileType.PARENT, avatarIndex = 1)
        )
        every { userRepository.getAll() } returns flowOf(profiles)

        // When
        val result = getProfilesUseCase().first()

        // Then
        assertEquals(2, result.size)
        assertEquals("Arjun", result[0].name)
    }
}
