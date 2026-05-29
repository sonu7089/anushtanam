/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * Domain representation of aggregated progress details for a child profile.
 */
data class ChildProgress(
    val child: UserProfile,
    val activeTarget: Target?,
    val totalRecitations: Int,
    val savings: Savings?,
    val recentSessions: List<RecitationSession> = emptyList()
)
