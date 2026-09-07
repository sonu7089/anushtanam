/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.domain.repository.AuthRepository
import com.mantrasiddhi.domain.repository.GoalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncrementActiveCloudGoalUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val goalRepository: GoalRepository = mockk(relaxed = true)
    private val useCase = IncrementActiveCloudGoalUseCase(authRepository, goalRepository)

    @Test
    fun `increments the cloud goal for a CHILD account`() = runTest {
        every { authRepository.currentAccount } returns flowOf(
            AuthAccount(uid = "c1", role = AccountRole.CHILD, displayName = "Aarav", linkCode = "123456789012")
        )
        coEvery { goalRepository.incrementActiveGoalProgress() } returns Result.success(true)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { goalRepository.incrementActiveGoalProgress() }
    }

    @Test
    fun `no-op for a PARENT account`() = runTest {
        every { authRepository.currentAccount } returns flowOf(
            AuthAccount(uid = "p1", role = AccountRole.PARENT, displayName = "Meera")
        )

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { goalRepository.incrementActiveGoalProgress() }
    }

    @Test
    fun `no-op when signed out`() = runTest {
        every { authRepository.currentAccount } returns flowOf(null)

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { goalRepository.incrementActiveGoalProgress() }
    }
}
