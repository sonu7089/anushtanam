/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.Purashcharana

/**
 * Mirrors the signed-in sadhak's Purashcharana sankalpa to the cloud.
 *
 * A Purashcharana is 24,00,000 japa — the work of years. Room stays the working store
 * and the source of truth for the screen; this mirror exists so that a reinstall, a new
 * phone, or "clear app data" cannot erase a count that took years to build. Every call
 * is best-effort: a failure here must never disturb japa counting.
 *
 * Unlike [TargetSyncRepository], the mirror writes the ABSOLUTE count rather than a
 * server-side increment (see [mirror]); that is what makes it idempotent, self-healing,
 * and safe to throttle.
 */
interface PurashcharanaSyncRepository {

    /**
     * Mirrors the sadhak's current sankalpa state.
     *
     * The write carries the absolute [Purashcharana.currentJapaCount] together with the
     * invariant fields, so a doc that was never created (enrolled while offline), a lost
     * write, or a replayed one all converge on the right number: the next mirror carries
     * the full total. An increment-based mirror that missed its create would silently
     * diverge forever, which is unacceptable for a number measured in years.
     *
     * Writes are THROTTLED. A write per japa would make this one feature dominate the
     * app's entire Firestore write budget, so an ordinary japa is only mirrored once per
     * throttle window; the state in between is held and pushed by the next mirror or by
     * [flush]. Because every write is absolute, a throttled-away write loses nothing
     * permanently — the worst case is a crash inside the window, which costs only the
     * japa recited in it.
     *
     * @param immediate bypasses the throttle. Pass `true` for the moments that must not
     *   wait: enrolling, withdrawing, crossing a milestone, and the Purnahuti.
     * No-op when [localUserId] is not the signed-in account's mapped local profile.
     */
    suspend fun mirror(
        localUserId: Long,
        sankalpa: Purashcharana,
        immediate: Boolean = false
    ): Result<Unit>

    /**
     * Writes whatever state the throttle is still holding for [localUserId], ignoring the
     * interval. Called when a japa session ends, so closing the app straight after
     * reciting does not strand the last minute of japa. A no-op when nothing is held.
     */
    suspend fun flush(localUserId: Long): Result<Unit>

    /**
     * Drops whatever snapshot the throttle is holding.
     *
     * Called when the login reconcile re-establishes local truth from the cloud, so a
     * snapshot captured BEFORE that restore — potentially for a different account, or
     * carrying a count the restore has just superseded — can never be written back
     * afterwards. Nothing is lost by dropping it: the reconcile has just raised Room to at
     * least the mirrored count, so the next mirror carries a value at least as high.
     */
    suspend fun discardPending()

    /**
     * The signed-in sadhak's mirrored sankalpa, or `null` when there is none / the record
     * is unusable / signed out / unreachable.
     *
     * Returned as a template with the local ids zeroed — exactly like
     * [TargetSyncRepository.fetchActive] — because the row it restores into is keyed by a
     * local Room profile id that only the caller knows.
     */
    suspend fun fetchMirrored(): Purashcharana?
}
