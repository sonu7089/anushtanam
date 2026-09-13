/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.ParentChildLink
import com.mantrasiddhi.domain.repository.LinkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Child: stream of link requests awaiting approval. */
class ObservePendingLinkRequestsUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    operator fun invoke(): Flow<List<ParentChildLink>> =
        linkRepository.pendingRequestsForChild()
}

class ApproveLinkUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Unit> =
        linkRepository.approveLink(linkId)
}

class RejectLinkUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Unit> =
        linkRepository.rejectLink(linkId)
}

/** Parent: stream of approved (linked) children. */
class ObserveLinkedChildrenUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    operator fun invoke(): Flow<List<ParentChildLink>> =
        linkRepository.approvedChildrenForParent()
}

/** Child: stream of approved (linked) parents. */
class ObserveLinkedParentsUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    operator fun invoke(): Flow<List<ParentChildLink>> =
        linkRepository.approvedParentsForChild()
}
