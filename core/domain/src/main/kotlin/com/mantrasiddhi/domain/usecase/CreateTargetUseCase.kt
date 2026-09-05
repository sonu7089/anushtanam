/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.repository.TargetSyncRepository
import javax.inject.Inject

/**
 * Use case to create a new recitation target for a user.
 *
 * Validates that the user has no active target, then creates the target. The
 * reward (if any) is descriptive only — there is no money ledger, so nothing is
 * locked; the parent is assumed to honour the promised reward on completion.
 * The signed-in user's own target is mirrored to the cloud (best-effort) so it
 * survives reinstall / device switch.
 */
class CreateTargetUseCase @Inject constructor(
    private val targetRepository: TargetRepository,
    private val targetSyncRepository: TargetSyncRepository
) {
    suspend operator fun invoke(target: Target): Result<Long> {
        val activeTarget = targetRepository.getActiveForUser(target.userId)
        if (activeTarget != null) {
            return Result.failure(IllegalStateException("An active target already exists for this profile."))
        }

        val id = targetRepository.create(target)
        runCatching { targetSyncRepository.mirrorCreate(target.userId, target) }
        return Result.success(id)
    }
}
