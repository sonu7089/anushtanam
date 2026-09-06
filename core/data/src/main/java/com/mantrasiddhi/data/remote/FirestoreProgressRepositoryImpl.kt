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
import com.mantrasiddhi.domain.repository.RecitationRepository
import com.mantrasiddhi.data.local.dao.RecitationDao
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.data.sync.CloudSyncOutbox
import com.mantrasiddhi.data.sync.CloudSyncPolicy
import com.mantrasiddhi.data.sync.SyncKind
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ProgressSyncRepository] over Firestore `progress/{uid}`.
 *
 * One small doc per user (~2-5 KB): headline totals plus a rolling `daily` map capped
 * at [ProgressSummary.DAILY_KEY_CAP] day keys. A durable Room outbox defers writes;
 * pending deltas are projected locally until their server checkpoint arrives.
 */
@Singleton
class FirestoreProgressRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val recitationRepository: RecitationRepository,
    private val recitationDao: RecitationDao,
    private val outbox: CloudSyncOutbox,
    private val syncDao: CloudSyncDao,
    private val userPreferences: UserPreferencesDataStore
) : ProgressSyncRepository {

    private val progress get() = firestore.collection(FirebaseContract.COLLECTION_PROGRESS)

    override fun summaryFlow(): Flow<ProgressSummary?> {
        // Same convention as the other cloud repos: resolve the uid when the flow is
        // requested; signed out means a single null emission and completion.
        val uid = auth.currentUser?.uid ?: return flowOf(null)
        return combine(progress.document(uid).snapshots(), syncDao.observePending(uid, SyncKind.PROGRESS)) { snap, pending ->
            if (!snap.exists()) null else {
                var projected: Map<String, Any?> = snap.data.orEmpty()
                @Suppress("UNCHECKED_CAST")
                val checkpoints = (projected[CloudSyncPolicy.CHECKPOINTS] as? Map<String, Any?>).orEmpty()
                for (event in pending) {
                    if (!CloudSyncPolicy.acknowledged(checkpoints, event)) {
                        projected = CloudSyncPolicy.progress(projected, CloudSyncPolicy.decode(event.payload))
                    }
                }
                projected.toSummary()
            }
        }.flowOn(Dispatchers.Default).catch { emit(null) }
    }

    /** Persists the compact delta and its seed before returning; never contacts the server. */
    override suspend fun recordSession(
        localUserId: Long,
        overallScore: Double,
        isCorrect: Boolean
    ): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        if (userPreferences.getLocalProfileId(uid) != localUserId) return@runCatching
        outbox.enqueue(uid, SyncKind.PROGRESS, mapOf(
            "score" to overallScore, "correct" to isCorrect,
            "date" to LocalDate.now().toString(), "seed" to buildSeedData(localUserId)
        ))
    }

    /**
     * One-time backfill of `progress/{uid}` from local Room history (totals, best
     * score, and per-day counts for the last [ProgressSummary.DAILY_KEY_CAP] days).
     *
     * Approximations for the seeded `daily` map, from what local aggregates expose:
     * - `c` is exact (the SQL aggregate counts correct sessions/day).
     * - `n` is approximated as `c` — per-day TOTAL attempt counts are not aggregated
     *   locally, so incorrect attempts before the seed are missing from `n`.
     * - `s` is approximated as `c * averageScore` (overall average as stand-in for
     *   the per-day score sum, which is not aggregated locally).
     * From the seed onward all three are maintained exactly by the increment path.
     */
    private suspend fun buildSeedData(localUserId: Long): Map<String, Any> {
        val stats = recitationRepository.getStats(localUserId)
        val bestScore = recitationDao.getBestScore(localUserId)
        val since = LocalDate.now().minusDays(ProgressSummary.DAILY_KEY_CAP.toLong() - 1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val counts = recitationDao.getCorrectDayCounts(localUserId, since)

        val daily = counts
            .filter { it.count > 0 }
            .associate { dayCount ->
                dayCount.dateKey to mapOf(
                    FirebaseContract.FIELD_DAY_SESSIONS to dayCount.count.toLong(),
                    FirebaseContract.FIELD_DAY_CORRECT to dayCount.count.toLong(),
                    FirebaseContract.FIELD_DAY_SCORE_SUM to dayCount.count * stats.averageScore
                )
            }

        return mapOf(
            FirebaseContract.FIELD_TOTAL_SESSIONS to stats.totalSessions.toLong(),
            FirebaseContract.FIELD_CORRECT_SESSIONS to stats.correctSessions.toLong(),
            // scoreSum reconstructed from the local average so avg survives the seed.
            FirebaseContract.FIELD_SCORE_SUM to stats.averageScore * stats.totalSessions,
            FirebaseContract.FIELD_BEST_SCORE to bestScore,
            FirebaseContract.FIELD_DAILY to daily
        )
    }

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
