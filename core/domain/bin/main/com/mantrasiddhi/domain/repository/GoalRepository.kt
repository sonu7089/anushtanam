/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.CloudGoal
import com.mantrasiddhi.domain.model.RewardType
import kotlinx.coroutines.flow.Flow

/**
 * Parent-set recitation goals for linked children, over Firestore `goals`.
 */
interface GoalRepository {
    /** Parent action: create a goal for [childUid] (requires an APPROVED link). */
    suspend fun createGoal(
        childUid: String,
        rewardType: RewardType,
        rewardDescription: String,
        targetCount: Int
    ): Result<Unit>

    /** Child stream: goals set for the signed-in child. */
    fun goalsForChild(childUid: String): Flow<List<CloudGoal>>

    /** Parent stream: goals the signed-in parent set for [childUid]. */
    fun goalsForParentAndChild(childUid: String): Flow<List<CloudGoal>>

    /** Retired anonymous API; implementations reject it. Save a stable session instead. */
    @Deprecated("Use RecitationRepository.saveSession; anonymous increments cannot be recovered")
    suspend fun incrementActiveGoalProgress(expectedChildUid: String): Result<Boolean>
}
