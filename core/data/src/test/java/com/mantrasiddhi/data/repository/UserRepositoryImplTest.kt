/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import com.mantrasiddhi.data.local.dao.UserDao
import com.mantrasiddhi.data.local.entity.UserProfileEntity
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserRepositoryImplTest {

    private lateinit var userDao: UserDao
    private lateinit var userRepository: UserRepositoryImpl

    @BeforeEach
    fun setUp() {
        userDao = mockk()
        userRepository = UserRepositoryImpl(userDao, Dispatchers.Unconfined)
    }

    @Test
    fun `should_getAllUsers_when_called`() = runTest {
        // Given
        val entities = listOf(
            UserProfileEntity(id = 1, name = "Arjun", profileType = "INDIVIDUAL", parentId = null, avatarIndex = 0)
        )
        every { userDao.getAll() } returns flowOf(entities)

        // When
        val result = userRepository.getAll().first()

        // Then
        assertEquals(1, result.size)
        assertEquals("Arjun", result[0].name)
        assertEquals(ProfileType.INDIVIDUAL, result[0].profileType)
    }

    @Test
    fun `should_getUserById_when_called`() = runTest {
        // Given
        val entity = UserProfileEntity(id = 2, name = "Meera", profileType = "PARENT", parentId = null, avatarIndex = 1)
        coEvery { userDao.getById(2L) } returns entity

        // When
        val result = userRepository.getById(2L)

        // Then
        assertEquals("Meera", result?.name)
        assertEquals(ProfileType.PARENT, result?.profileType)
    }

    @Test
    fun `should_getChildren_when_called`() = runTest {
        // Given
        val entities = listOf(
            UserProfileEntity(id = 3, name = "Aarav", profileType = "CHILD", parentId = 2L, avatarIndex = 2)
        )
        every { userDao.getChildrenOf(2L) } returns flowOf(entities)

        // When
        val result = userRepository.getChildrenOf(2L).first()

        // Then
        assertEquals(1, result.size)
        assertEquals("Aarav", result[0].name)
        assertEquals(2L, result[0].parentId)
    }
}
