/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val currentUser: StateFlow<UserProfile?>
    fun login(user: UserProfile)
    fun logout()
}
