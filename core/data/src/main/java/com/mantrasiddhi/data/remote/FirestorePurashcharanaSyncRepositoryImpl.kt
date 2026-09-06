/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mantrasiddhi.data.local.entity.PurashcharanaEntity
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.domain.repository.PurashcharanaSyncRepository
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PurashcharanaSyncRepository] over Firestore `purashcharana/{uid}` — one small doc
 * holding the sadhak's sankalpa so a count built over years survives reinstall / device
 * switch.
 *
 * ## Absolute count, written monotonically
 * `selfTargets` mirrors a recitation with `FieldValue.increment(1)`. This doc deliberately
 * does NOT: it writes the whole local count, which
 * - creates the doc when it is missing (enrolled while offline, first sync after an
 *   upgrade), so there is no create step that can be lost, and
 * - is idempotent: a replayed or dropped write converges on the right number, because the
 *   next mirror carries the full total again.
 * The invariant fields (akshara count, target, start, active/completed) ride along on
 * every write, so even a doc created by a stray write heals into a complete, restorable
 * record.
 *
 * An absolute write is idempotent but NOT ordered, so on its own it can be written by a
 * device whose count is behind and destroy the cloud total. Every write therefore goes
 * through a transaction that keeps `max(stored, local)` for the same sankalpa — see
 * [writePending]. That is what makes the count safe; it is not an optimisation. It also
 * makes two devices reciting concurrently merely lose the smaller device's surplus rather
 * than the whole history, and the login reconcile then raises both.
 *
 * ## Throttle
 * A full Purashcharana is 24,00,000 japa; one write per japa would make this single
 * feature dominate the app's Firestore budget. Ordinary japa is therefore mirrored at
 * most once per [PURASHCHARANA_SYNC_MIN_INTERVAL_MS], with the latest state held in
 * [pending] until the window opens, the next milestone lands, or [flush] is called at the
 * end of a japa session. Because each write is absolute, a throttled-away write loses
 * NOTHING permanently — the only loss case is a crash inside the window, which costs just
 * the japa recited during it. That is the whole reason the throttle is safe.
 */
@Singleton
class FirestorePurashcharanaSyncRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userPreferencesDataStore: UserPreferencesDataStore
) : PurashcharanaSyncRepository {

    private val purashcharana get() = firestore.collection(FirebaseContract.COLLECTION_PURASHCHARANA)

    /** Wall-clock time of the last mirror that actually reached Firestore. */
    @Volatile
    private var lastMirrorAtMillis: Long = 0L

    /**
     * The latest state not yet written. Held (rather than passed to [flush] by the caller)
     * because the cloud layer has no access to Room; a duplicate write from a rare race
     * between the japa path and a flush is harmless, since the write is absolute.
     */
    @Volatile
    private var pending: PendingMirror? = null

    private data class PendingMirror(val localUserId: Long, val sankalpa: Purashcharana)

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
        pending = PendingMirror(localUserId, sankalpa)
        val elapsed = System.currentTimeMillis() - lastMirrorAtMillis
        if (!immediate && elapsed < PURASHCHARANA_SYNC_MIN_INTERVAL_MS) return@runCatching
        writePending(localUserId)
    }

    override suspend fun flush(localUserId: Long): Result<Unit> = runCatching {
        writePending(localUserId)
    }

    /**
     * Writes the held state for [localUserId], if any, as a MONOTONIC transaction.
     *
     * The transaction is the load-bearing part of this class, not an optimisation. An
     * absolute write is idempotent but not ordered: any device carrying a count lower than
     * the cloud's — a fresh enrol after an offline login, a phone restored from an old
     * backup, a second device mid-catch-up — would otherwise overwrite the cloud total with
     * its own smaller one, and years of japa would be gone with nothing left to recover
     * them from. Reconciling on READ cannot save that, because by the next login the higher
     * number no longer exists anywhere. So the merge happens HERE, at the write, where the
     * stored value is still visible:
     *
     * - Same sankalpa still running in the cloud -> keep `max(stored, local)` for the count
     *   and the EARLIER start, so a write can raise the cloud but never lower it. This also
     *   makes a withdrawal from a behind device safe: `isActive` still flips to false while
     *   the higher count is preserved.
     * - Anything else (no doc, or a withdrawn / different vow) -> write local wholesale, so
     *   re-taking the sankalpa after withdrawing legitimately starts again from zero.
     *
     * A transaction needs the server, so this cannot run offline — deliberately. A plain
     * `set()` would be parked in Firestore's offline queue and applied on reconnect with
     * whatever stale count it captured, which is the exact write we must never make. Failing
     * instead costs nothing: Room holds the truth, [pending] survives, and the next mirror
     * carries the full total again.
     */
    private suspend fun writePending(localUserId: Long) {
        val held = pending?.takeIf { it.localUserId == localUserId } ?: return
        val uid = uidForOwnProfile(localUserId) ?: return
        val sankalpa = held.sankalpa
        val document = purashcharana.document(uid)

        // Bounded wait. This runs inline on the single evaluation-queue consumer that scores
        // recitations, so it must never park that coroutine on a slow or dead network — the
        // mirror is best-effort and the sadhak's japa must keep counting regardless.
        val acked = withTimeoutOrNull(MIRROR_WRITE_TIMEOUT_MS) {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(document)
                val storedIsSameSankalpa = snapshot.exists() &&
                    snapshot.getBoolean(FirebaseContract.FIELD_IS_ACTIVE) != false &&
                    // Docs written before the app carried a catalog have no mantra id; they
                    // can only be the Gayatri, which is what the local row says too.
                    storedMantraId(snapshot) == sankalpa.mantraId &&
                    (snapshot.getLong(FirebaseContract.FIELD_AKSHARA_COUNT) ?: 0L).toInt() ==
                        sankalpa.mantraAksharaCount &&
                    (snapshot.getLong(FirebaseContract.FIELD_TARGET_JAPA_COUNT) ?: 0L) ==
                        sankalpa.targetJapaCount

                val storedCount = snapshot.getLong(FirebaseContract.FIELD_CURRENT_JAPA_COUNT) ?: 0L
                val storedStart = snapshot.getLong(FirebaseContract.FIELD_STARTED_AT) ?: 0L
                val storedCompletedAt = snapshot.getLong(FirebaseContract.FIELD_COMPLETED_AT)

                val mergedCount = if (storedIsSameSankalpa) {
                    maxOf(storedCount, sankalpa.currentJapaCount)
                } else {
                    sankalpa.currentJapaCount
                }
                // The sankalpa began when it began. A zeroed/absent stored start is ignored
                // rather than treated as the epoch.
                val mergedStart = if (
                    storedIsSameSankalpa && storedStart in 1L until sankalpa.startedAtMillis
                ) {
                    storedStart
                } else {
                    sankalpa.startedAtMillis
                }
                // Keep the earliest Purnahuti stamp, but only while the merged count actually
                // reaches the target — a stamp without the japa behind it would claim a vow
                // was fulfilled when it was not.
                val mergedCompletedAt = if (storedIsSameSankalpa) {
                    listOfNotNull(sankalpa.completedAtMillis, storedCompletedAt)
                        .minOrNull()
                        ?.takeIf { mergedCount >= sankalpa.targetJapaCount }
                } else {
                    sankalpa.completedAtMillis
                }

                transaction.set(
                    document,
                    mapOf(
                        FirebaseContract.FIELD_MANTRA_ID to sankalpa.mantraId,
                        FirebaseContract.FIELD_AKSHARA_COUNT to sankalpa.mantraAksharaCount.toLong(),
                        FirebaseContract.FIELD_TARGET_JAPA_COUNT to sankalpa.targetJapaCount,
                        FirebaseContract.FIELD_CURRENT_JAPA_COUNT to mergedCount,
                        FirebaseContract.FIELD_STARTED_AT to mergedStart,
                        // Written as an explicit null while in progress so a doc that once
                        // carried a stamp cannot keep a stale one under merge().
                        FirebaseContract.FIELD_COMPLETED_AT to mergedCompletedAt,
                        // Local wins: this is how a withdrawal is recorded, and it must be
                        // honoured even from a device whose count was behind.
                        FirebaseContract.FIELD_IS_ACTIVE to sankalpa.isActive,
                        // Denormalised for the rules / any future function; the client always
                        // derives completion from the stamp and the count, never from this.
                        FirebaseContract.FIELD_IS_COMPLETED to
                            (mergedCount >= sankalpa.targetJapaCount),
                        FirebaseContract.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                mergedCount
            }.awaitResult()
            true
        } == true

        // Stamp the ATTEMPT, not just the acknowledgement. Offline, every single japa would
        // otherwise re-arm a transaction that cannot succeed; the throttle has to close
        // either way.
        lastMirrorAtMillis = System.currentTimeMillis()
        // Clear ONLY the snapshot this write actually carried. flush() runs on its own
        // coroutine, so mirror() may have replaced [pending] with newer japa while the round
        // trip was in flight — that state is unwritten and must survive. Reference identity,
        // not equals(): mirror() always assigns a fresh instance.
        if (acked && pending === held) pending = null
    }

    override suspend fun fetchMirrored(): Purashcharana? = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching null
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
        pending = null
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

    private companion object {
        /** Shortest gap between two ordinary (non-milestone) mirrors. */
        const val PURASHCHARANA_SYNC_MIN_INTERVAL_MS = 60_000L

        /**
         * Longest a japa may wait on the cloud mirror. The write is absolute and the
         * transaction is monotonic, so giving up early loses nothing — the next mirror or
         * flush carries the full total again.
         */
        const val MIRROR_WRITE_TIMEOUT_MS = 5_000L
    }
}
