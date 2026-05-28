/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * Domain representation of virtual savings for a user profile.
 */
data class Savings(
    val userId: Long,
    val totalSaved: Double = 0.0,
    val totalLocked: Double = 0.0,
    val totalUnlocked: Double = 0.0
)
