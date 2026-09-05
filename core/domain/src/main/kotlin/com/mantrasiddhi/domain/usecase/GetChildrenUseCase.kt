/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase to retrieve children profiles associated with a parent user profile.
 */
class GetChildrenUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    operator fun invoke(parentId: Long): Flow<List<UserProfile>> {
        return userRepository.getChildrenOf(parentId)
    }
}
