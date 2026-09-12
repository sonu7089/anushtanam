/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import com.mantrasiddhi.data.sync.CloudSyncOutbox
import com.mantrasiddhi.data.sync.SyncKind
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.mantrasiddhi.domain.model.CloudGoal
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.repository.GoalRepository
import com.mantrasiddhi.domain.repository.AuthRepository
import com.mantrasiddhi.domain.diagnostics.WorkProbe
import kotlinx.coroutines.CoroutineScope
import com.mantrasiddhi.di.AccountScope
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
    private val firestore: FirebaseFirestore,
    private val outbox: CloudSyncOutbox,
    accounts: AuthRepository,
    @AccountScope scope: CoroutineScope
) : GoalRepository {

    private val goals get() = firestore.collection(FirebaseContract.COLLECTION_GOALS)
    private val childGoals = SharedChildGoals(
        accounts.currentAccount,
        scope,
        currentUid = { auth.currentUser?.uid },
        source = ::loadChildGoals
    )

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

    override fun goalsForChild(childUid: String): Flow<List<CloudGoal>> = childGoals.observe(childUid)

    private fun loadChildGoals(childUid: String): Flow<List<CloudGoal>> {
        return WorkProbe.observe("goals.child", goals
            .whereEqualTo(FirebaseContract.FIELD_CHILD_UID, childUid)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toGoal() }.sortedBy { it.createdAt ?: 0L } }
            .catch { emit(emptyList()) })
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

    @Deprecated("Assigned-goal credit is committed with a stable saved session")
    override suspend fun incrementActiveGoalProgress(expectedChildUid: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("A stable saved session is required"))

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
