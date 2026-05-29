/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * UseCase to log in a user profile.
 */
class SelectProfileUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(user: UserProfile) {
        sessionRepository.login(user)
    }
}
