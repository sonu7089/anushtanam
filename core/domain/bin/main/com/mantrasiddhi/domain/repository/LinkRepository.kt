/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.ParentChildLink
import kotlinx.coroutines.flow.Flow

/**
 * Parent <-> child linking over Firestore `links`.
 */
interface LinkRepository {
    /** Child creates a short-lived, single-use invitation. It contains no login credentials. */
    suspend fun createQrInvite(): Result<com.mantrasiddhi.domain.model.FamilyQrInvite>
    suspend fun linkWithQr(payload: String): Result<Unit>
    fun observeQrInvite(token: String): Flow<com.mantrasiddhi.domain.model.FamilyQrInvite?>

    /** Child action stream: link requests awaiting this child's approval. */
    fun pendingRequestsForChild(): Flow<List<ParentChildLink>>

    suspend fun approveLink(linkId: String): Result<Unit>
    suspend fun rejectLink(linkId: String): Result<Unit>

    /** Parent's approved children. */
    fun approvedChildrenForParent(): Flow<List<ParentChildLink>>

    /** Child's approved parents. */
    fun approvedParentsForChild(): Flow<List<ParentChildLink>>
}
