/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Savings
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.SavingsRepository
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
    private lateinit var savingsRepository: SavingsRepository
    private lateinit var createTargetUseCase: CreateTargetUseCase

    @BeforeEach
    fun setUp() {
        targetRepository = mockk(relaxed = true)
        savingsRepository = mockk(relaxed = true)
        createTargetUseCase = CreateTargetUseCase(targetRepository, savingsRepository)
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
    fun `should_lockSavingsAndCreateTarget_when_noActiveTargetExists`() = runTest {
        // Given
        coEvery { targetRepository.getActiveForUser(1L) } returns null
        coEvery { savingsRepository.getSavingsForUser(1L) } returns Savings(userId = 1L, totalLocked = 0.0)
        coEvery { targetRepository.create(any()) } returns 100L

        val targetToCreate = Target(
            userId = 1L,
            setByUserId = 1L,
            targetRecitationCount = 20,
            currentRecitationCount = 0,
            moneyAmount = 150.0,
            isCompleted = false
        )

        // When
        val result = createTargetUseCase(targetToCreate)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(100L, result.getOrNull())
        coVerify(exactly = 1) { targetRepository.create(targetToCreate) }
        coVerify(exactly = 1) { 
            savingsRepository.updateSavings(Savings(userId = 1L, totalLocked = 150.0, totalSaved = 0.0, totalUnlocked = 0.0)) 
        }
    }
}
