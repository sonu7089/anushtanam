/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import com.mantrasiddhi.data.sync.CloudSyncOutbox
import com.mantrasiddhi.data.sync.SyncKind
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.repository.TargetSyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TargetSyncRepository] over Firestore `selfTargets/{uid}` — one tiny doc holding
 * the signed-in user's active self-target so the Recitation tab's goal survives
 * reinstall / device switch.
 *
 * The `localUserId -> uid` guard uses the same DataStore mapping the auth bridge
 * maintains, so a parent's device never mirrors a LOCAL child profile's target
 * under the parent's uid. Increments are server-side so recitations from two
 * devices (or queued offline) add up instead of overwriting.
 */
@Singleton
class FirestoreTargetSyncRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val outbox: CloudSyncOutbox,
    private val syncDao: CloudSyncDao
) : TargetSyncRepository {

    private val selfTargets get() = firestore.collection(FirebaseContract.COLLECTION_SELF_TARGETS)

    /** The signed-in uid, but only when [localUserId] is that account's mapped local profile. */
    private suspend fun uidForOwnProfile(localUserId: Long): String? {
        val uid = auth.currentUser?.uid ?: return null
        val mappedId = userPreferencesDataStore.getLocalProfileId(uid) ?: return null
        return uid.takeIf { mappedId == localUserId }
    }

    override suspend fun mirrorCreate(localUserId: Long, target: Target): Result<Unit> = runCatching {
        val uid = uidForOwnProfile(localUserId) ?: return@runCatching
        outbox.enqueue(uid, SyncKind.TARGET_CREATE,
            mapOf(
                FirebaseContract.FIELD_TARGET_COUNT to target.targetRecitationCount.toLong(),
                FirebaseContract.FIELD_CURRENT_COUNT to target.currentRecitationCount.toLong(),
                FirebaseContract.FIELD_REWARD_TYPE to target.rewardType.name,
                FirebaseContract.FIELD_REWARD_DESCRIPTION to target.rewardDescription,
                FirebaseContract.FIELD_IS_COMPLETED to target.isCompleted
            )
        )
        Unit
    }

    override suspend fun mirrorIncrement(localUserId: Long, isCompleted: Boolean): Result<Unit> =
        runCatching {
            val uid = uidForOwnProfile(localUserId) ?: return@runCatching
            outbox.enqueue(uid, SyncKind.TARGET_INCREMENT, emptyMap())
            Unit
        }

    override suspend fun clearMirror(localUserId: Long): Result<Unit> = runCatching {
        val uid = uidForOwnProfile(localUserId) ?: return@runCatching
        outbox.enqueue(uid, SyncKind.TARGET_CLEAR, emptyMap())
        Unit
    }

    override suspend fun fetchActive(): Target? = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching null
        // Do not restore an old cloud target over a pending local completion/withdrawal.
        if (syncDao.hasKinds(uid, listOf(SyncKind.TARGET_CREATE, SyncKind.TARGET_INCREMENT, SyncKind.TARGET_CLEAR))) return@runCatching null
        val snap = selfTargets.document(uid).get().awaitResult()
        if (!snap.exists()) return@runCatching null
        if (snap.getBoolean(FirebaseContract.FIELD_IS_COMPLETED) == true) return@runCatching null
        Target(
            id = 0,
            userId = 0,
            setByUserId = 0,
            targetRecitationCount = (snap.getLong(FirebaseContract.FIELD_TARGET_COUNT) ?: 0L).toInt(),
            currentRecitationCount = (snap.getLong(FirebaseContract.FIELD_CURRENT_COUNT) ?: 0L).toInt(),
            rewardType = RewardType.fromName(snap.getString(FirebaseContract.FIELD_REWARD_TYPE)),
            rewardDescription = snap.getString(FirebaseContract.FIELD_REWARD_DESCRIPTION) ?: "",
            isCompleted = false
        ).takeIf { it.targetRecitationCount > 0 }
    }.getOrNull()
}
