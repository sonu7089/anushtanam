/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * Domain representation of a recitation target.
 */
data class Target(
    val id: Long = 0,
    val userId: Long,
    val setByUserId: Long,
    val targetRecitationCount: Int,
    val currentRecitationCount: Int = 0,
    val rewardType: RewardType = RewardType.OTHER,
    val rewardDescription: String = "",
    val isCompleted: Boolean = false
) {
    /** True when a specific reward has been described (blank + OTHER means "no reward set"). */
    val hasReward: Boolean
        get() = rewardDescription.isNotBlank()

    val progress: Float
        get() = if (targetRecitationCount > 0) {
            currentRecitationCount.toFloat() / targetRecitationCount.toFloat()
        } else {
            0f
        }
}
