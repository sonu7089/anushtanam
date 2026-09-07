/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.TargetRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetActiveTargetUseCaseTest {

    private lateinit var targetRepository: TargetRepository
    private lateinit var getActiveTargetUseCase: GetActiveTargetUseCase

    @BeforeEach
    fun setUp() {
        targetRepository = mockk()
        getActiveTargetUseCase = GetActiveTargetUseCase(targetRepository)
    }

    @Test
    fun `should_returnActiveTarget_when_activeTargetExists`() = runTest {
        // Given
        val target = Target(id = 1, userId = 2, setByUserId = 2, targetRecitationCount = 108, currentRecitationCount = 5)
        coEvery { targetRepository.getActiveForUser(2L) } returns target

        // When
        val result = getActiveTargetUseCase(2L)

        // Then
        assertEquals(target, result)
    }

    @Test
    fun `should_returnNull_when_noActiveTargetExists`() = runTest {
        // Given
        coEvery { targetRepository.getActiveForUser(2L) } returns null

        // When
        val result = getActiveTargetUseCase(2L)

        // Then
        assertNull(result)
    }
}
