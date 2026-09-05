/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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
    private val userPreferencesDataStore: UserPreferencesDataStore
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
        selfTargets.document(uid).set(
            mapOf(
                FirebaseContract.FIELD_TARGET_COUNT to target.targetRecitationCount.toLong(),
                FirebaseContract.FIELD_CURRENT_COUNT to target.currentRecitationCount.toLong(),
                FirebaseContract.FIELD_REWARD_TYPE to target.rewardType.name,
                FirebaseContract.FIELD_REWARD_DESCRIPTION to target.rewardDescription,
                FirebaseContract.FIELD_IS_COMPLETED to target.isCompleted,
                FirebaseContract.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            )
        ).awaitResult()
        Unit
    }

    override suspend fun mirrorIncrement(localUserId: Long, isCompleted: Boolean): Result<Unit> =
        runCatching {
            val uid = uidForOwnProfile(localUserId) ?: return@runCatching
            selfTargets.document(uid).update(
                mapOf(
                    FirebaseContract.FIELD_CURRENT_COUNT to FieldValue.increment(1L),
                    FirebaseContract.FIELD_IS_COMPLETED to isCompleted,
                    FirebaseContract.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            ).awaitResult()
            Unit
        }

    override suspend fun clearMirror(localUserId: Long): Result<Unit> = runCatching {
        val uid = uidForOwnProfile(localUserId) ?: return@runCatching
        selfTargets.document(uid).delete().awaitResult()
        Unit
    }

    override suspend fun fetchActive(): Target? = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching null
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
