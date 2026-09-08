/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.mantrasiddhi.domain.model.LinkStatus
import com.mantrasiddhi.domain.model.ParentChildLink
import com.mantrasiddhi.domain.repository.LinkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LinkRepository] over Firestore `links` and `childCodes`.
 */
@Singleton
class FirestoreLinkRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val directLinks: DirectFamilyLinkService
) : LinkRepository {

    private val links get() = firestore.collection(FirebaseContract.COLLECTION_LINKS)
    private val users get() = firestore.collection(FirebaseContract.COLLECTION_USERS)
    private val childCodes get() = firestore.collection(FirebaseContract.COLLECTION_CHILD_CODES)

    override suspend fun linkWithPin(childCode: String, pin: String) = directLinks.linkWithPin(childCode, pin)
    override suspend fun createQrInvite() = directLinks.createQrInvite()
    override suspend fun linkWithQr(payload: String) = directLinks.linkWithQr(payload)
    override fun observeQrInvite(token: String) = directLinks.observeQrInvite(token)

    override suspend fun requestLink(childCode: String): Result<Unit> = runCatching {
        val parentUid = auth.currentUser?.uid
            ?: error("Not signed in")
        val code = childCode.trim()
        require(code.length == FirebaseContract.CODE_LENGTH && code.all(Char::isDigit)) {
            "Enter a valid ${FirebaseContract.CODE_LENGTH}-digit ID"
        }

        val codeSnap = childCodes.document(code).get().awaitResult()
        val childUid = codeSnap.getString(FirebaseContract.FIELD_UID)
            ?: error("No child found for that ID")
        require(childUid != parentUid) { "You cannot link to yourself" }

        val linkId = FirebaseContract.linkId(parentUid, childUid)
        // Tolerant read: the security rules deny reading a link doc that doesn't exist
        // yet (the rule dereferences resource.data), so a first-time request would throw
        // PERMISSION_DENIED here. Treat any read failure as "no existing link, proceed".
        val existing = runCatching { links.document(linkId).get().awaitResult() }.getOrNull()
        if (existing != null && existing.exists()) {
            val status = LinkStatus.fromName(existing.getString(FirebaseContract.FIELD_STATUS))
            require(status == LinkStatus.REJECTED) {
                if (status == LinkStatus.APPROVED) "You are already linked to this child"
                else "A request is already pending for this child"
            }
        }

        // Only the parent's own name is set here — the security rules forbid reading the
        // child's users doc before the link is APPROVED. The child stamps their own name
        // onto the link when they approve it (see updateStatus).
        val parentName = users.document(parentUid).get().awaitResult()
            .getString(FirebaseContract.FIELD_DISPLAY_NAME) ?: ""

        val data = hashMapOf(
            FirebaseContract.FIELD_PARENT_UID to parentUid,
            FirebaseContract.FIELD_CHILD_UID to childUid,
            FirebaseContract.FIELD_PARENT_NAME to parentName,
            FirebaseContract.FIELD_CHILD_NAME to "",
            FirebaseContract.FIELD_STATUS to LinkStatus.PENDING.name,
            FirebaseContract.FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            FirebaseContract.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
        links.document(linkId).set(data).awaitResult()
        Unit
    }

    override fun pendingRequestsForChild(): Flow<List<ParentChildLink>> {
        val childUid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return links
            .whereEqualTo(FirebaseContract.FIELD_CHILD_UID, childUid)
            .whereEqualTo(FirebaseContract.FIELD_STATUS, LinkStatus.PENDING.name)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toLink() } }
            .catch { emit(emptyList()) }
    }

    override suspend fun approveLink(linkId: String): Result<Unit> =
        updateStatus(linkId, LinkStatus.APPROVED)

    override suspend fun rejectLink(linkId: String): Result<Unit> =
        updateStatus(linkId, LinkStatus.REJECTED)

    private suspend fun updateStatus(linkId: String, status: LinkStatus): Result<Unit> = runCatching {
        val update = mutableMapOf<String, Any>(
            FirebaseContract.FIELD_STATUS to status.name,
            FirebaseContract.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
        // On approval, the child stamps their own name onto the link so the parent can
        // display it (the parent could not read the child's name before approval). The
        // child reading its own users doc is permitted by the rules.
        if (status == LinkStatus.APPROVED) {
            val childUid = auth.currentUser?.uid
            if (childUid != null) {
                val ownName = runCatching {
                    users.document(childUid).get().awaitResult()
                        .getString(FirebaseContract.FIELD_DISPLAY_NAME)
                }.getOrNull()
                if (!ownName.isNullOrBlank()) {
                    update[FirebaseContract.FIELD_CHILD_NAME] = ownName
                }
            }
        }
        links.document(linkId).update(update).awaitResult()
        Unit
    }

    override fun approvedChildrenForParent(): Flow<List<ParentChildLink>> {
        val parentUid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return links
            .whereEqualTo(FirebaseContract.FIELD_PARENT_UID, parentUid)
            .whereEqualTo(FirebaseContract.FIELD_STATUS, LinkStatus.APPROVED.name)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toLink() } }
            .catch { emit(emptyList()) }
    }

    override fun approvedParentsForChild(): Flow<List<ParentChildLink>> {
        val childUid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return links
            .whereEqualTo(FirebaseContract.FIELD_CHILD_UID, childUid)
            .whereEqualTo(FirebaseContract.FIELD_STATUS, LinkStatus.APPROVED.name)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toLink() } }
            .catch { emit(emptyList()) }
    }

    private fun DocumentSnapshot.toLink(): ParentChildLink? {
        val parentUid = getString(FirebaseContract.FIELD_PARENT_UID) ?: return null
        val childUid = getString(FirebaseContract.FIELD_CHILD_UID) ?: return null
        return ParentChildLink(
            id = id,
            parentUid = parentUid,
            childUid = childUid,
            childName = getString(FirebaseContract.FIELD_CHILD_NAME) ?: "",
            parentName = getString(FirebaseContract.FIELD_PARENT_NAME) ?: "",
            status = LinkStatus.fromName(getString(FirebaseContract.FIELD_STATUS)),
            createdAt = getTimestamp(FirebaseContract.FIELD_CREATED_AT)?.toDate()?.time
        )
    }
}
