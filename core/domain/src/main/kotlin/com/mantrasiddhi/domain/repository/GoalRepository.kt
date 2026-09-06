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
    fun goalsForChild(): Flow<List<CloudGoal>>

    /** Parent stream: goals the signed-in parent set for [childUid]. */
    fun goalsForParentAndChild(childUid: String): Flow<List<CloudGoal>>

    /**
     * Child action: increment the child's active (non-completed, oldest) goal by 1,
     * marking it completed once the target is reached. No-op when no active goal exists.
     *
     * The durable implementation queues this locally and returns false. Completion
     * arrives through goalsForChild after the deferred upload.
     */
    suspend fun incrementActiveGoalProgress(): Result<Boolean>
}
