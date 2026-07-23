/*
 * Anushtanam - Gayatri Mantra Recitation App
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
import com.mantrasiddhi.domain.model.CloudGoal
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GoalRepository] over Firestore `goals`.
 */
@Singleton
class FirestoreGoalRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : GoalRepository {

    private val goals get() = firestore.collection(FirebaseContract.COLLECTION_GOALS)

    override suspend fun createGoal(
        childUid: String,
        rewardType: RewardType,
        rewardDescription: String,
        targetCount: Int
    ): Result<Unit> = runCatching {
        val parentUid = auth.currentUser?.uid ?: error("Not signed in")
        require(targetCount > 0) { "Target count must be greater than zero" }
        val data = hashMapOf(
            FirebaseContract.FIELD_PARENT_UID to parentUid,
            FirebaseContract.FIELD_CHILD_UID to childUid,
            FirebaseContract.FIELD_REWARD_TYPE to rewardType.name,
            FirebaseContract.FIELD_REWARD_DESCRIPTION to rewardDescription.trim(),
            FirebaseContract.FIELD_TARGET_COUNT to targetCount,
            FirebaseContract.FIELD_CURRENT_COUNT to 0,
            FirebaseContract.FIELD_IS_COMPLETED to false,
            FirebaseContract.FIELD_CREATED_AT to FieldValue.serverTimestamp()
        )
        goals.add(data).awaitResult()
        Unit
    }

    override fun goalsForChild(): Flow<List<CloudGoal>> {
        val childUid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return goals
            .whereEqualTo(FirebaseContract.FIELD_CHILD_UID, childUid)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toGoal() }.sortedBy { it.createdAt ?: 0L } }
            .catch { emit(emptyList()) }
    }

    override fun goalsForParentAndChild(childUid: String): Flow<List<CloudGoal>> {
        val parentUid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return goals
            .whereEqualTo(FirebaseContract.FIELD_PARENT_UID, parentUid)
            .whereEqualTo(FirebaseContract.FIELD_CHILD_UID, childUid)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toGoal() }.sortedBy { it.createdAt ?: 0L } }
            .catch { emit(emptyList()) }
    }

    override suspend fun incrementActiveGoalProgress(): Result<Boolean> = runCatching {
        val childUid = auth.currentUser?.uid ?: error("Not signed in")
        // Oldest non-completed goal is the "active" one.
        val snap = goals
            .whereEqualTo(FirebaseContract.FIELD_CHILD_UID, childUid)
            .whereEqualTo(FirebaseContract.FIELD_IS_COMPLETED, false)
            .get()
            .awaitResult()
        val active = snap.documents
            .mapNotNull { it.toGoal() }
            .minByOrNull { it.createdAt ?: Long.MAX_VALUE }
            ?: return@runCatching false // no active goal — nothing to do

        val newCount = active.currentRecitationCount + 1
        val completed = newCount >= active.targetRecitationCount
        goals.document(active.id).update(
            mapOf(
                FirebaseContract.FIELD_CURRENT_COUNT to newCount,
                FirebaseContract.FIELD_IS_COMPLETED to completed
            )
        ).awaitResult()
        // The active goal was non-completed by query filter, so `completed` here means
        // this increment is the one that just completed it.
        completed
    }

    private fun DocumentSnapshot.toGoal(): CloudGoal? {
        val parentUid = getString(FirebaseContract.FIELD_PARENT_UID) ?: return null
        val childUid = getString(FirebaseContract.FIELD_CHILD_UID) ?: return null
        return CloudGoal(
            id = id,
            parentUid = parentUid,
            childUid = childUid,
            rewardType = RewardType.fromName(getString(FirebaseContract.FIELD_REWARD_TYPE)),
            rewardDescription = getString(FirebaseContract.FIELD_REWARD_DESCRIPTION) ?: "",
            targetRecitationCount = (getLong(FirebaseContract.FIELD_TARGET_COUNT) ?: 0L).toInt(),
            currentRecitationCount = (getLong(FirebaseContract.FIELD_CURRENT_COUNT) ?: 0L).toInt(),
            isCompleted = getBoolean(FirebaseContract.FIELD_IS_COMPLETED) ?: false,
            createdAt = getTimestamp(FirebaseContract.FIELD_CREATED_AT)?.toDate()?.time
        )
    }
}
