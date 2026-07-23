/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.model.CloudGoal
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.repository.AuthRepository
import com.mantrasiddhi.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Parent: create a recitation goal for a linked child. */
class CreateChildGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(
        childUid: String,
        rewardType: RewardType,
        rewardDescription: String,
        targetCount: Int
    ): Result<Unit> =
        goalRepository.createGoal(childUid, rewardType, rewardDescription, targetCount)
}

/** Child: stream of goals set for the signed-in child. */
class ObserveChildGoalsUseCase @Inject constructor(
    private val goalRepository: GoalRepository
) {
    operator fun invoke(): Flow<List<CloudGoal>> = goalRepository.goalsForChild()
}

/** Parent: stream of goals the signed-in parent set for a given child. */
class ObserveGoalsForChildUseCase @Inject constructor(
    private val goalRepository: GoalRepository
) {
    operator fun invoke(childUid: String): Flow<List<CloudGoal>> =
        goalRepository.goalsForParentAndChild(childUid)
}

/**
 * Increments the signed-in CHILD's active cloud goal by one, guarded so it only runs
 * when the current account is actually a CHILD. Safe to call on every correct
 * recitation; a no-op for parents or when no active goal exists.
 *
 * @return `true` when this increment just completed the child's active cloud goal;
 *   `false` otherwise (still in progress, no active goal, or not a CHILD account).
 */
class IncrementActiveCloudGoalUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(): Result<Boolean> {
        val account = authRepository.currentAccount.first()
        if (account?.role != AccountRole.CHILD) return Result.success(false)
        return goalRepository.incrementActiveGoalProgress()
    }
}
