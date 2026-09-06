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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.mantrasiddhi.data.local.entity.PurashcharanaEntity
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.domain.repository.PurashcharanaSyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Durable, deferred snapshots of the active sankalpa. Server merging never lowers its count. */
@Singleton
class FirestorePurashcharanaSyncRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val outbox: CloudSyncOutbox,
    private val syncDao: CloudSyncDao
) : PurashcharanaSyncRepository {

    private val purashcharana get() = firestore.collection(FirebaseContract.COLLECTION_PURASHCHARANA)

    /** The signed-in uid, but only when [localUserId] is that account's mapped local profile. */
    private suspend fun uidForOwnProfile(localUserId: Long): String? {
        val uid = auth.currentUser?.uid ?: return null
        val mappedId = userPreferencesDataStore.getLocalProfileId(uid) ?: return null
        return uid.takeIf { mappedId == localUserId }
    }

    override suspend fun mirror(
        localUserId: Long,
        sankalpa: Purashcharana,
        immediate: Boolean
    ): Result<Unit> = runCatching {
        val uid = uidForOwnProfile(localUserId) ?: return@runCatching
        outbox.enqueue(uid, SyncKind.PURASHCHARANA, mapOf(
            FirebaseContract.FIELD_MANTRA_ID to sankalpa.mantraId,
            FirebaseContract.FIELD_AKSHARA_COUNT to sankalpa.mantraAksharaCount,
            FirebaseContract.FIELD_TARGET_JAPA_COUNT to sankalpa.targetJapaCount,
            FirebaseContract.FIELD_CURRENT_JAPA_COUNT to sankalpa.currentJapaCount,
            FirebaseContract.FIELD_STARTED_AT to sankalpa.startedAtMillis,
            FirebaseContract.FIELD_COMPLETED_AT to sankalpa.completedAtMillis,
            FirebaseContract.FIELD_IS_ACTIVE to sankalpa.isActive,
            FirebaseContract.FIELD_IS_COMPLETED to sankalpa.isCompleted
        ))
    }

    override suspend fun enqueueMirror(localUserId: Long, sankalpa: Purashcharana, immediate: Boolean): Result<Unit> =
        mirror(localUserId, sankalpa, immediate)

    // All snapshots are already durable. Stopping the mic does not bypass the one-minute cadence.
    override suspend fun flush(localUserId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun fetchMirrored(): Purashcharana? = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching null
        if (syncDao.hasKinds(uid, listOf(SyncKind.PURASHCHARANA))) return@runCatching null
        val snap = purashcharana.document(uid).get().awaitResult()
        if (!snap.exists()) return@runCatching null

        val aksharaCount = (snap.getLong(FirebaseContract.FIELD_AKSHARA_COUNT) ?: 0L).toInt()
        val targetJapaCount = snap.getLong(FirebaseContract.FIELD_TARGET_JAPA_COUNT) ?: 0L
        val startedAtMillis = snap.getLong(FirebaseContract.FIELD_STARTED_AT) ?: 0L
        // A record missing any of these cannot be restored into a meaningful sankalpa —
        // in particular a zeroed start would make every pace and projection nonsense.
        if (aksharaCount <= 0 || targetJapaCount <= 0L || startedAtMillis <= 0L) {
            return@runCatching null
        }

        Purashcharana(
            id = 0L,
            userId = 0L,
            mantraId = storedMantraId(snap),
            mantraAksharaCount = aksharaCount,
            targetJapaCount = targetJapaCount,
            currentJapaCount = (snap.getLong(FirebaseContract.FIELD_CURRENT_JAPA_COUNT) ?: 0L)
                .coerceIn(0L, targetJapaCount),
            startedAtMillis = startedAtMillis,
            completedAtMillis = snap.getLong(FirebaseContract.FIELD_COMPLETED_AT),
            // Older docs predate the field; absent means the sankalpa was never withdrawn.
            isActive = snap.getBoolean(FirebaseContract.FIELD_IS_ACTIVE) != false
        )
    }.getOrNull()

    override suspend fun discardPending() {
        // Durable unacknowledged counts must survive login reconciliation. The upload
        // transaction preserves the larger count and skips acknowledged operations.
    }

    /**
     * The mantra a mirrored sankalpa is for.
     *
     * Docs written before the catalog existed carry no `mantraId`; those vows were
     * necessarily the Gayatri, the only mantra the app then had, and reading them as such
     * is what lets an existing sadhak's sankalpa still reconcile after this upgrade.
     */
    private fun storedMantraId(snapshot: DocumentSnapshot): String =
        snapshot.getString(FirebaseContract.FIELD_MANTRA_ID)?.takeIf { it.isNotBlank() }
            ?: PurashcharanaEntity.LEGACY_MANTRA_ID

}
