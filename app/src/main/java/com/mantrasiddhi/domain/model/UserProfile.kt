/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * Domain representation of a user profile.
 */
data class UserProfile(
    val id: Long = 0,
    val name: String,
    val profileType: ProfileType,
    val parentId: Long? = null,
    val avatarIndex: Int
) {
    val canSetOwnGoal: Boolean
        get() = profileType == ProfileType.INDIVIDUAL || profileType == ProfileType.PARENT

    val canSetChildGoal: Boolean
        get() = profileType == ProfileType.PARENT

    val canSeeChildProgress: Boolean
        get() = profileType == ProfileType.PARENT

    val canRecite: Boolean
        get() = true
}
