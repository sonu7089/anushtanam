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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IncrementTargetUseCaseTest {

    private lateinit var targetRepository: TargetRepository
    private lateinit var savingsRepository: SavingsRepository
    private lateinit var incrementTargetUseCase: IncrementTargetUseCase

    @BeforeEach
    fun setUp() {
        targetRepository = mockk(relaxed = true)
        savingsRepository = mockk(relaxed = true)
        incrementTargetUseCase = IncrementTargetUseCase(targetRepository, savingsRepository)
    }

    @Test
    fun `should_returnNull_when_noActiveTargetExists`() = runTest {
        // Given
        coEvery { targetRepository.getActiveForUser(1L) } returns null

        // When
        val result = incrementTargetUseCase(1L)

        // Then
        assertNull(result)
        coVerify(exactly = 0) { targetRepository.update(any()) }
    }

    @Test
    fun `should_incrementCountAndNotMarkCompleted_when_targetCountNotReached`() = runTest {
        // Given
        val target = Target(
            id = 10L,
            userId = 1L,
            setByUserId = 1L,
            targetRecitationCount = 10,
            currentRecitationCount = 5,
            isCompleted = false
        )
        coEvery { targetRepository.getActiveForUser(1L) } returns target

        // When
        val result = incrementTargetUseCase(1L)

        // Then
        assertTrue(result != null)
        assertEquals(6, result?.currentRecitationCount)
        assertFalse(result?.isCompleted == true)
        coVerify(exactly = 1) { targetRepository.update(result!!) }
    }

    @Test
    fun `should_incrementCountAndMarkCompleted_when_targetCountReached`() = runTest {
        // Given
        val target = Target(
            id = 10L,
            userId = 1L,
            setByUserId = 1L,
            targetRecitationCount = 10,
            currentRecitationCount = 9,
            isCompleted = false
        )
        coEvery { targetRepository.getActiveForUser(1L) } returns target

        // When
        val result = incrementTargetUseCase(1L)

        // Then
        assertTrue(result != null)
        assertEquals(10, result?.currentRecitationCount)
        assertTrue(result?.isCompleted == true)
        coVerify(exactly = 1) { targetRepository.update(result!!) }
    }

    @Test
    fun `should_unlockMoney_when_targetCountReachedAndHasMoneyAmount`() = runTest {
        // Given
        val target = Target(
            id = 10L,
            userId = 1L,
            setByUserId = 1L,
            targetRecitationCount = 10,
            currentRecitationCount = 9,
            moneyAmount = 500.0,
            isCompleted = false
        )
        val initialSavings = Savings(userId = 1L, totalLocked = 500.0, totalSaved = 0.0, totalUnlocked = 0.0)
        coEvery { targetRepository.getActiveForUser(1L) } returns target
        coEvery { savingsRepository.getSavingsForUser(1L) } returns initialSavings

        // When
        val result = incrementTargetUseCase(1L)

        // Then
        assertTrue(result != null)
        assertTrue(result?.isCompleted == true)
        
        val expectedSavings = Savings(userId = 1L, totalLocked = 0.0, totalSaved = 500.0, totalUnlocked = 500.0)
        coVerify(exactly = 1) { savingsRepository.updateSavings(expectedSavings) }
    }
}
