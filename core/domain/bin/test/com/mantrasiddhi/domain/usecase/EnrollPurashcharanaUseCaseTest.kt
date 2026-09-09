/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.domain.model.PurashcharanaPath
import com.mantrasiddhi.domain.repository.PurashcharanaRepository
import com.mantrasiddhi.domain.repository.PurashcharanaSyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Enrolling is the one moment that produces a local count of ZERO, which makes it the one
 * moment this device can be behind the cloud by an entire sadhana — a sadhak who signed in
 * while offline (so the login reconcile was skipped) and then takes the sankalpa afresh.
 *
 * If that zero is allowed to become the working count, the very next japa counts 1 and the
 * years already recorded are gone. That exact path shipped once and was caught in review
 * with no test covering it, so it is pinned here.
 */
class EnrollPurashcharanaUseCaseTest {

    private val userId = 7L
    private val gayatriTarget = PurashcharanaPath.targetFor(24)

    private val repository: PurashcharanaRepository = mockk(relaxed = true)
    private val syncRepository: PurashcharanaSyncRepository = mockk(relaxed = true)
    private val useCase = EnrollPurashcharanaUseCase(repository, syncRepository, gayatri)

    init {
        coEvery { repository.getActive(userId) } returns null
        coEvery { repository.adoptRemote(any(), any()) } returns Result.success(Unit)
    }

    /** The row `enroll` inserts: always zero japa, always started now. */
    private fun freshlyEnrolled(startedAtMillis: Long = 1_700_000_000_000L) = Purashcharana(
        id = 3L,
        userId = userId,
        mantraId = gayatri.id,
        mantraAksharaCount = 24,
        targetJapaCount = gayatriTarget,
        currentJapaCount = 0L,
        startedAtMillis = startedAtMillis,
        completedAtMillis = null,
        isActive = true
    )

    private fun mirrored(current: Long, startedAtMillis: Long) = Purashcharana(
        id = 0L,
        userId = 0L,
        mantraId = gayatri.id,
        mantraAksharaCount = 24,
        targetJapaCount = gayatriTarget,
        currentJapaCount = current,
        startedAtMillis = startedAtMillis,
        completedAtMillis = null,
        isActive = true
    )

    @Test
    fun `enrolling over a mirror that holds years of japa adopts it instead of starting at zero`() =
        runTest {
            val enrolledAt = 1_700_000_000_000L
            val trueStart = enrolledAt - 400L * 24L * 60L * 60L * 1000L
            coEvery { repository.enroll(userId, gayatri.id, 24) } returns
                Result.success(freshlyEnrolled(enrolledAt))
            coEvery { syncRepository.fetchMirrored() } returns mirrored(500_000L, trueStart)

            useCase(userId)

            val adopted = slot<Purashcharana>()
            coVerify(exactly = 1) { repository.adoptRemote(userId, capture(adopted)) }
            assertEquals(500_000L, adopted.captured.currentJapaCount)
            // The sankalpa began when it began — not on the day this device re-enrolled.
            assertEquals(trueStart, adopted.captured.startedAtMillis)
        }

    @Test
    fun `enrolling over a mirror that holds japa never writes the zero back to the cloud`() =
        runTest {
            coEvery { repository.enroll(userId, gayatri.id, 24) } returns Result.success(freshlyEnrolled())
            coEvery { syncRepository.fetchMirrored() } returns
                mirrored(500_000L, 1_600_000_000_000L)

            useCase(userId)

            coVerify(exactly = 0) { syncRepository.mirror(any(), any(), any()) }
        }

    @Test
    fun `a genuinely new sankalpa is mirrored at once so the day it was taken is not lost`() =
        runTest {
            val fresh = freshlyEnrolled()
            coEvery { repository.enroll(userId, gayatri.id, 24) } returns Result.success(fresh)
            coEvery { syncRepository.fetchMirrored() } returns null

            useCase(userId)

            coVerify(exactly = 1) { syncRepository.mirror(userId, fresh, true) }
            coVerify(exactly = 0) { repository.adoptRemote(any(), any()) }
        }

    @Test
    fun `a withdrawn mirror is not adopted — setting a sankalpa aside means starting over`() =
        runTest {
            val fresh = freshlyEnrolled()
            coEvery { repository.enroll(userId, gayatri.id, 24) } returns Result.success(fresh)
            coEvery { syncRepository.fetchMirrored() } returns
                mirrored(500_000L, 1_600_000_000_000L).copy(isActive = false)

            useCase(userId)

            coVerify(exactly = 0) { repository.adoptRemote(any(), any()) }
            coVerify(exactly = 1) { syncRepository.mirror(userId, fresh, true) }
        }

    @Test
    fun `an unreachable mirror still enrols locally`() = runTest {
        val fresh = freshlyEnrolled()
        coEvery { repository.enroll(userId, gayatri.id, 24) } returns Result.success(fresh)
        coEvery { syncRepository.fetchMirrored() } throws IllegalStateException("offline")

        val result = useCase(userId)

        assertEquals(fresh, result.getOrNull())
    }

    @Test
    fun `a failed local enrol touches neither the mirror nor Room`() = runTest {
        coEvery { repository.enroll(userId, gayatri.id, 24) } returns
            Result.failure(IllegalStateException("a sankalpa is already running"))

        val result = useCase(userId)

        assertEquals(true, result.isFailure)
        coVerify(exactly = 0) { syncRepository.mirror(any(), any(), any()) }
        coVerify(exactly = 0) { repository.adoptRemote(any(), any()) }
    }
}
