/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.Target

/**
 * Mirrors the signed-in user's ACTIVE SELF-TARGET to the cloud so it survives
 * reinstall / device switch (Room remains the working store; the mirror is
 * best-effort). Only the user's own target is mirrored — a parent setting a target
 * for a local child profile on the same device is not (that flow's cross-device
 * counterpart is the cloud goals system).
 */
interface TargetSyncRepository {

    /** Mirrors a freshly created self-target. No-op when [localUserId] isn't the signed-in user's local profile. */
    suspend fun mirrorCreate(localUserId: Long, target: Target): Result<Unit>

    /** Advances the mirrored target by one recitation, marking completion when reached. */
    suspend fun mirrorIncrement(localUserId: Long, isCompleted: Boolean): Result<Unit>

    /** Removes the mirror (target abandoned). */
    suspend fun clearMirror(localUserId: Long): Result<Unit>

    /**
     * The signed-in user's mirrored active target, or null when none / completed /
     * signed out / unreachable. Returned as a template with local ids zeroed —
     * the caller re-keys it to the local profile when restoring into Room.
     */
    suspend fun fetchActive(): Target?
}
