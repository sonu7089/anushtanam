/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.mantrasiddhi.domain.model.DayAggregate
import com.mantrasiddhi.domain.model.ProgressSummary
import com.mantrasiddhi.domain.repository.ProgressSyncRepository
import com.mantrasiddhi.domain.repository.RecitationRepository
import com.mantrasiddhi.domain.usecase.GetDailyRecitationCountsUseCase
import com.mantrasiddhi.domain.usecase.GetRecitationStatsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ProgressSyncRepository] over Firestore `progress/{uid}`.
 *
 * One small doc per user (~2-5 KB): headline totals plus a rolling `daily` map capped
 * at [ProgressSummary.DAILY_KEY_CAP] day keys. Firestore's default offline
 * persistence queues writes while offline; no extra persistence config is added.
 */
@Singleton
class FirestoreProgressRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val recitationRepository: RecitationRepository,
    private val getRecitationStatsUseCase: GetRecitationStatsUseCase,
    private val getDailyRecitationCountsUseCase: GetDailyRecitationCountsUseCase
) : ProgressSyncRepository {

    private val progress get() = firestore.collection(FirebaseContract.COLLECTION_PROGRESS)

    override fun summaryFlow(): Flow<ProgressSummary?> {
        // Same convention as the other cloud repos: resolve the uid when the flow is
        // requested; signed out means a single null emission and completion.
        val uid = auth.currentUser?.uid ?: return flowOf(null)
        return progress.document(uid)
            .snapshots()
            .map { it.toSummary() }
            .catch { emit(null) }
    }

    override suspend fun recordSession(
        localUserId: Long,
        overallScore: Double,
        isCorrect: Boolean
    ): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val doc = progress.document(uid)

        // Server-or-cache read; also serves as the cached baseline for bestScore
        // compare and daily-key pruning.
        val snap = doc.get().awaitResult()
        if (!snap.exists()) {
            // First session ever seen for this uid on any device: seed from local
            // history. recordSession() runs AFTER the session was saved to Room, so
            // the local stats already include the triggering session — seeding from
            // them and SKIPPING the increment step counts it exactly once.
            doc.set(buildSeedData(localUserId)).awaitResult()
            return@runCatching
        }

        val dateKey = LocalDate.now().toString()
        val dayPath = "${FirebaseContract.FIELD_DAILY}.$dateKey"
        val updates = mutableMapOf<String, Any>(
            FirebaseContract.FIELD_TOTAL_SESSIONS to FieldValue.increment(1L),
            FirebaseContract.FIELD_CORRECT_SESSIONS to FieldValue.increment(if (isCorrect) 1L else 0L),
            FirebaseContract.FIELD_SCORE_SUM to FieldValue.increment(overallScore),
            "$dayPath.${FirebaseContract.FIELD_DAY_SESSIONS}" to FieldValue.increment(1L),
            "$dayPath.${FirebaseContract.FIELD_DAY_CORRECT}" to FieldValue.increment(if (isCorrect) 1L else 0L),
            "$dayPath.${FirebaseContract.FIELD_DAY_SCORE_SUM}" to FieldValue.increment(overallScore),
            FirebaseContract.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )

        // bestScore: read-compare-write against the cached value; only sent when this
        // session beats it (a concurrent-device race can only under-report briefly —
        // the next better session corrects it).
        val cachedBest = snap.getDouble(FirebaseContract.FIELD_BEST_SCORE) ?: 0.0
        if (overallScore > cachedBest) {
            updates[FirebaseContract.FIELD_BEST_SCORE] = overallScore
        }

        // Prune: keep only the newest DAILY_KEY_CAP day keys (ISO keys sort
        // chronologically). Today's key is always among the newest, so it is never
        // deleted by the same update that increments it.
        val existingKeys = (snap.get(FirebaseContract.FIELD_DAILY) as? Map<*, *>)
            ?.keys?.filterIsInstance<String>().orEmpty()
        val allKeys = (existingKeys + dateKey).distinct().sorted()
        if (allKeys.size > ProgressSummary.DAILY_KEY_CAP) {
            allKeys.dropLast(ProgressSummary.DAILY_KEY_CAP).forEach { stale ->
                updates["${FirebaseContract.FIELD_DAILY}.$stale"] = FieldValue.delete()
            }
        }

        doc.update(updates).awaitResult()
        Unit
    }

    /**
     * One-time backfill of `progress/{uid}` from local Room history (totals, best
     * score, and per-day counts for the last [ProgressSummary.DAILY_KEY_CAP] days).
     *
     * Approximations for the seeded `daily` map, from what local aggregates expose:
     * - `c` is exact (GetDailyRecitationCountsUseCase counts correct sessions/day).
     * - `n` is approximated as `c` — per-day TOTAL attempt counts are not aggregated
     *   locally, so incorrect attempts before the seed are missing from `n`.
     * - `s` is approximated as `c * averageScore` (overall average as stand-in for
     *   the per-day score sum, which is not aggregated locally).
     * From the seed onward all three are maintained exactly by the increment path.
     */
    private suspend fun buildSeedData(localUserId: Long): Map<String, Any> {
        val stats = recitationRepository.getStats(localUserId)
        val overview = getRecitationStatsUseCase(localUserId).first()
        val counts = getDailyRecitationCountsUseCase(localUserId, ProgressSummary.DAILY_KEY_CAP).first()
        val zone = ZoneId.systemDefault()

        val daily = counts
            .filter { it.count > 0 }
            .associate { dayCount ->
                val key = Instant.ofEpochMilli(dayCount.dateMillis).atZone(zone).toLocalDate().toString()
                key to mapOf(
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
            FirebaseContract.FIELD_BEST_SCORE to overview.bestSessionScore,
            FirebaseContract.FIELD_DAILY to daily,
            FirebaseContract.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
    }

    private fun DocumentSnapshot.toSummary(): ProgressSummary? {
        if (!exists()) return null
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
            totalSessions = getLong(FirebaseContract.FIELD_TOTAL_SESSIONS) ?: 0L,
            correctSessions = getLong(FirebaseContract.FIELD_CORRECT_SESSIONS) ?: 0L,
            scoreSum = getDouble(FirebaseContract.FIELD_SCORE_SUM) ?: 0.0,
            bestScore = getDouble(FirebaseContract.FIELD_BEST_SCORE) ?: 0.0,
            daily = daily
        )
    }
}
