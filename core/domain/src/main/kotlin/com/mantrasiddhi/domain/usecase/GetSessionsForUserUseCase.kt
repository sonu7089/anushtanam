/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase to get recitation sessions for a user.
 */
class GetSessionsForUserUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository
) {
    operator fun invoke(userId: Long): Flow<List<RecitationSession>> {
        return recitationRepository.getSessionsForUser(userId)
    }
}
