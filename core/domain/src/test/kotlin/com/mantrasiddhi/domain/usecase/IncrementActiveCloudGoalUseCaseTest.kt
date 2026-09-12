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
        coEvery { goalRepository.incrementActiveGoalProgress(any()) } returns Result.success(true)

        val result = useCase("c1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { goalRepository.incrementActiveGoalProgress(any()) }
    }

    @Test
    fun `no-op for a PARENT account`() = runTest {
        every { authRepository.currentAccount } returns flowOf(
            AuthAccount(uid = "p1", role = AccountRole.PARENT, displayName = "Meera")
        )

        val result = useCase("c1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { goalRepository.incrementActiveGoalProgress(any()) }
    }

    @Test
    fun `no-op when signed out`() = runTest {
        every { authRepository.currentAccount } returns flowOf(null)

        val result = useCase("c1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { goalRepository.incrementActiveGoalProgress(any()) }
    }
    @Test fun `saved owner cannot increment another signed in child`() = runTest {
        every { authRepository.currentAccount } returns flowOf(AuthAccount("B", AccountRole.CHILD, "B"))
        assertTrue(useCase("A").isSuccess)
        coVerify(exactly = 0) { goalRepository.incrementActiveGoalProgress(any()) }
    }

    @Test fun `local only recitation never reads cloud account state`() = runTest {
        assertTrue(useCase(null).isSuccess)
        io.mockk.verify(exactly = 0) { authRepository.currentAccount }
        coVerify(exactly = 0) { goalRepository.incrementActiveGoalProgress(any()) }
    }

    @Test fun `twenty child attempts preserve twenty goal updates for the saved owner`() = runTest {
        every { authRepository.currentAccount } returns kotlinx.coroutines.flow.MutableStateFlow(
            AuthAccount("c1", AccountRole.CHILD, "Aarav"))
        coEvery { goalRepository.incrementActiveGoalProgress("c1") } returns Result.success(false)
        repeat(20) { assertTrue(useCase("c1").isSuccess) }
        coVerify(exactly = 20) { goalRepository.incrementActiveGoalProgress("c1") }
        coVerify(exactly = 0) { authRepository.refreshAccount() }
    }

}
