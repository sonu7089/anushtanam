/*
 * Anushtanam - Gayatri Mantra Recitation App
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
    /**
     * Parent action: resolve [childCode] (12-digit ID) to a child uid and create a
     * PENDING link. Fails on self-link, unknown code, or an existing pending/approved link.
     */
    suspend fun requestLink(childCode: String): Result<Unit>

    /** Child action stream: link requests awaiting this child's approval. */
    fun pendingRequestsForChild(): Flow<List<ParentChildLink>>

    suspend fun approveLink(linkId: String): Result<Unit>
    suspend fun rejectLink(linkId: String): Result<Unit>

    /** Parent's approved children. */
    fun approvedChildrenForParent(): Flow<List<ParentChildLink>>

    /** Child's approved parents. */
    fun approvedParentsForChild(): Flow<List<ParentChildLink>>
}
