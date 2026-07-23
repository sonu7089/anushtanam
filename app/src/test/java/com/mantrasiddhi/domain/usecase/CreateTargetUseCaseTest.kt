/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.TargetRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CreateTargetUseCaseTest {

    private lateinit var targetRepository: TargetRepository
    private lateinit var createTargetUseCase: CreateTargetUseCase

    @BeforeEach
    fun setUp() {
        targetRepository = mockk(relaxed = true)
        createTargetUseCase = CreateTargetUseCase(targetRepository)
    }

    @Test
    fun `should_fail_when_activeTargetAlreadyExists`() = runTest {
        // Given
        val existingTarget = Target(
            id = 1L,
            userId = 1L,
            setByUserId = 1L,
            targetRecitationCount = 10,
            currentRecitationCount = 0,
            isCompleted = false
        )
        coEvery { targetRepository.getActiveForUser(1L) } returns existingTarget

        val targetToCreate = Target(
            userId = 1L,
            setByUserId = 1L,
            targetRecitationCount = 20,
            currentRecitationCount = 0,
            isCompleted = false
        )

        // When
        val result = createTargetUseCase(targetToCreate)

        // Then
        assertTrue(result.isFailure)
        assertEquals("An active target already exists for this profile.", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { targetRepository.create(any()) }
    }

    @Test
    fun `should_createTarget_when_noActiveTargetExists`() = runTest {
        // Given
        coEvery { targetRepository.getActiveForUser(1L) } returns null
        coEvery { targetRepository.create(any()) } returns 100L

        val targetToCreate = Target(
            userId = 1L,
            setByUserId = 1L,
            targetRecitationCount = 20,
            currentRecitationCount = 0,
            rewardType = RewardType.GIFT,
            rewardDescription = "New cricket bat",
            isCompleted = false
        )

        // When
        val result = createTargetUseCase(targetToCreate)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(100L, result.getOrNull())
        coVerify(exactly = 1) { targetRepository.create(targetToCreate) }
    }
}
