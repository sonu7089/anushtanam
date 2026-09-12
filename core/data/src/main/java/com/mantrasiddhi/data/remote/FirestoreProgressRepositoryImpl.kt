/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.mantrasiddhi.domain.model.DayAggregate
import com.mantrasiddhi.domain.model.ProgressSummary
import com.mantrasiddhi.domain.repository.ProgressSyncRepository
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import com.mantrasiddhi.data.sync.CloudSyncPolicy
import com.mantrasiddhi.data.sync.SyncKind
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ProgressSyncRepository] over Firestore `progress/{uid}`.
 *
 * One small doc per user (~2-5 KB): headline totals plus a rolling `daily` map capped
 * at [ProgressSummary.DAILY_KEY_CAP] day keys. A durable Room outbox defers writes;
 * the UI uses Room history while session receipts are awaiting acknowledgement.
 */
@Singleton
class FirestoreProgressRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val syncDao: CloudSyncDao
) : ProgressSyncRepository {

    private val progress get() = firestore.collection(FirebaseContract.COLLECTION_PROGRESS)

    override fun summaryFlow(): Flow<ProgressSummary?> {
        // Same convention as the other cloud repos: resolve the uid when the flow is
        // requested; signed out means a single null emission and completion.
        val uid = auth.currentUser?.uid ?: return flowOf(null)
        return combine(progress.document(uid).snapshots(), syncDao.observePending(uid, SyncKind.PRACTICE_RECORD)) { snap, pending ->
            if (!snap.exists() || snap.getLong(com.mantrasiddhi.data.sync.SessionProjectionPolicy.VERSION_KEY) !=
                com.mantrasiddhi.data.sync.SessionProjectionPolicy.VERSION) null else {
                val projected: Map<String, Any?> = snap.data.orEmpty()
                @Suppress("UNCHECKED_CAST")
                val checkpoints = (projected[CloudSyncPolicy.CHECKPOINTS] as? Map<String, Any?>).orEmpty()
                // A captured historical session may already be included in bootstrap.
                // Fall back to Room, never add an unproven anonymous pending delta.
                if (pending.any { CloudSyncPolicy.decode(it.payload)["type"] == "session" &&
                    !CloudSyncPolicy.acknowledged(checkpoints, it) }) null else projected.toSummary()
            }
        }.flowOn(Dispatchers.Default).catch { emit(null) }
    }

    @Deprecated("Counting is committed with RecitationRepository.saveSession")
    override suspend fun recordSession(localUserId: Long, overallScore: Double, isCorrect: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("A stable saved session is required"))

    private fun Map<String, Any?>.toSummary(): ProgressSummary {
        val dailyRaw = get(FirebaseContract.FIELD_DAILY) as? Map<*, *> ?: emptyMap<Any, Any>()
        val daily = dailyRaw.entries.mapNotNull { (key, value) ->
            val dateKey = key as? String ?: return@mapNotNull null
            val entry = value as? Map<*, *> ?: return@mapNotNull null
            dateKey to DayAggregate(
                n = (entry[FirebaseContract.FIELD_DAY_SESSIONS] as? Number)?.toLong() ?: 0L,
                c = (entry[FirebaseContract.FIELD_DAY_CORRECT] as? Number)?.toLong() ?: 0L,
                s = (entry[FirebaseContract.FIELD_DAY_SCORE_SUM] as? Number)?.toDouble() ?: 0.0
            )
        }.toMap()
        return ProgressSummary(
            totalSessions = (get(FirebaseContract.FIELD_TOTAL_SESSIONS) as? Number)?.toLong() ?: 0L,
            correctSessions = (get(FirebaseContract.FIELD_CORRECT_SESSIONS) as? Number)?.toLong() ?: 0L,
            scoreSum = (get(FirebaseContract.FIELD_SCORE_SUM) as? Number)?.toDouble() ?: 0.0,
            bestScore = (get(FirebaseContract.FIELD_BEST_SCORE) as? Number)?.toDouble() ?: 0.0,
            daily = daily
        )
    }
}
