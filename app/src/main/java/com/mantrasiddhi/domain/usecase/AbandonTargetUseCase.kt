/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.repository.TargetSyncRepository
import javax.inject.Inject

/**
 * Use case to abandon/delete the active target for a user.
 *
 * There is no money ledger, so abandoning simply removes the active target and
 * clears its cloud mirror so it is not restored on the next login.
 */
class AbandonTargetUseCase @Inject constructor(
    private val targetRepository: TargetRepository,
    private val targetSyncRepository: TargetSyncRepository
) {
    suspend operator fun invoke(userId: Long): Boolean {
        val activeTarget = targetRepository.getActiveForUser(userId) ?: return false
        targetRepository.delete(activeTarget.id)
        runCatching { targetSyncRepository.clearMirror(userId) }
        return true
    }
}
