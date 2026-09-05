/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * A recitation goal a parent set for a linked child, stored in Firestore.
 *
 * The reward semantics mirror the local [Target]/[RewardType] model: no money ledger,
 * the reward is simply considered earned once the target count is reached.
 */
data class CloudGoal(
    val id: String,
    val parentUid: String,
    val childUid: String,
    val rewardType: RewardType,
    val rewardDescription: String,
    val targetRecitationCount: Int,
    val currentRecitationCount: Int,
    val isCompleted: Boolean,
    val createdAt: Long? = null
) {
    val progress: Float
        get() = if (targetRecitationCount <= 0) 0f
        else (currentRecitationCount.toFloat() / targetRecitationCount).coerceIn(0f, 1f)

    val hasReward: Boolean
        get() = rewardDescription.isNotBlank()
}
