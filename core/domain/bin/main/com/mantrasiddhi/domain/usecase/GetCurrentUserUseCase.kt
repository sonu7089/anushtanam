/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.SessionRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * UseCase to get the currently active user profile.
 */
class GetCurrentUserUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): StateFlow<UserProfile?> {
        return sessionRepository.currentUser
    }
}
