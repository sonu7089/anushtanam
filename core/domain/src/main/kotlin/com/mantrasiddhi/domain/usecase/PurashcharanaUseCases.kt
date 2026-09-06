/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.mantra.ActiveMantra
import com.mantrasiddhi.domain.mantra.Mantra
import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.domain.model.PurashcharanaPath
import com.mantrasiddhi.domain.repository.PurashcharanaRepository
import com.mantrasiddhi.domain.repository.PurashcharanaSyncRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams the sadhak's active Purashcharana, or `null` when none has been taken. */
class GetActivePurashcharanaUseCase @Inject constructor(
    private val purashcharanaRepository: PurashcharanaRepository
) {
    operator fun invoke(userId: Long): Flow<Purashcharana?> =
        purashcharanaRepository.observeActive(userId)
}

/**
 * Takes the Purashcharana sankalpa for the mantra the app is currently teaching.
 *
 * The mantra is not a parameter: a sankalpa is taken for the mantra being recited, and
 * its length is what the vow is reckoned against. Fails when a sankalpa is already
 * active.
 *
 * The new sankalpa is mirrored to the cloud at once (not throttled): the day the vow was
 * taken is the one fact the sadhak can never recover if the device is lost before the
 * first ordinary mirror.
 */
class EnrollPurashcharanaUseCase @Inject constructor(
    private val purashcharanaRepository: PurashcharanaRepository,
    private val purashcharanaSyncRepository: PurashcharanaSyncRepository,
    @ActiveMantra private val mantra: Mantra
) {
    suspend operator fun invoke(userId: Long): Result<Purashcharana> {
        val result = purashcharanaRepository.enroll(userId, mantra.id, mantra.aksharaCount)
        val fresh = result.getOrNull() ?: return result
        runCatching {
            // Enrolling is the ONE moment that produces a local count of zero, so it is the
            // one moment this device can be behind the cloud by an entire sadhana: a sadhak
            // who signs in offline (the login reconcile is skipped) and then takes the
            // sankalpa afresh has a Room row at 0 while the mirror still holds years of
            // japa. The transactional write can no longer lower the cloud, but the LOCAL row
            // would still read 0 and count up from there, so adopt the mirror here too —
            // the same pure decision the login reconcile uses.
            val remote = purashcharanaSyncRepository.fetchMirrored()
            when (val action = PurashcharanaReconciler.resolve(fresh, remote)) {
                is PurashcharanaReconcileAction.RaiseLocal ->
                    purashcharanaRepository.adoptRemote(userId, action.merged)
                // Nothing to adopt — no mirror, a withdrawn one, or a different vow. The day
                // the sankalpa was taken is the one fact that cannot be recovered if this
                // device is lost, so it goes up at once rather than waiting for the throttle.
                else ->
                    purashcharanaSyncRepository.mirror(userId, fresh, immediate = true)
            }
        }
        return result
    }
}

/**
 * Adds one verified japa to the active sankalpa.
 *
 * Safe to call after every correct NORMAL-mode recitation: it is a no-op returning
 * `null` for a sadhak who has not enrolled.
 *
 * The cloud mirror is best-effort and deliberately invisible to the counting path — a
 * signed-out user, an offline device or a Firestore outage leaves the local count exactly
 * as correct as it was. It is throttled except on the japa that lands on a milestone (the
 * Purnahuti among them), because those are the moments worth a guaranteed write.
 */
class IncrementPurashcharanaUseCase @Inject constructor(
    private val purashcharanaRepository: PurashcharanaRepository,
    private val purashcharanaSyncRepository: PurashcharanaSyncRepository
) {
    suspend operator fun invoke(userId: Long): Result<Purashcharana?> {
        // Read before incrementing so the mirror can tell a real advance from a japa that
        // changed nothing (the DAO caps the count at the target, so a fulfilled sankalpa
        // would otherwise re-mirror the same milestone count on every recitation).
        val before = purashcharanaRepository.getActive(userId)
        val result = purashcharanaRepository.incrementJapa(userId)
        val after = result.getOrNull()
        if (after != null && after.currentJapaCount != before?.currentJapaCount) {
            runCatching {
                purashcharanaSyncRepository.mirror(
                    localUserId = userId,
                    sankalpa = after,
                    immediate = PurashcharanaMirrorPolicy.landsOnMilestone(after)
                )
            }
        }
        return result
    }
}

/** Withdraws from the sankalpa. The japa already recited is kept as history. */
class WithdrawPurashcharanaUseCase @Inject constructor(
    private val purashcharanaRepository: PurashcharanaRepository,
    private val purashcharanaSyncRepository: PurashcharanaSyncRepository
) {
    suspend operator fun invoke(userId: Long): Result<Unit> {
        // Snapshot first: once the row is deactivated there is nothing left to read, and
        // the mirror must record BOTH the withdrawal and the count reached before it, so
        // a later login does not restore a sankalpa the sadhak has set aside.
        val active = purashcharanaRepository.getActive(userId)
        val result = purashcharanaRepository.withdraw(userId)
        if (result.isSuccess && active != null) {
            runCatching {
                purashcharanaSyncRepository.mirror(
                    localUserId = userId,
                    sankalpa = active.copy(isActive = false),
                    immediate = true
                )
            }
        }
        return result
    }
}

/**
 * Pushes any japa the mirror's throttle is still holding.
 *
 * Called when a japa session ends, so closing the app right after reciting does not
 * strand the last minute of japa in local storage only.
 */
class FlushPurashcharanaSyncUseCase @Inject constructor(
    private val purashcharanaSyncRepository: PurashcharanaSyncRepository
) {
    suspend operator fun invoke(userId: Long): Result<Unit> =
        purashcharanaSyncRepository.flush(userId)
}

/** Decides which japa are worth a guaranteed cloud write rather than a throttled one. */
object PurashcharanaMirrorPolicy {

    /**
     * True when [sankalpa]'s count sits exactly on a milestone.
     *
     * The count moves one japa at a time, so landing exactly on a milestone count *is*
     * the crossing — no before/after comparison is needed, and the last milestone is the
     * target itself, which makes this cover the Purnahuti too.
     */
    fun landsOnMilestone(sankalpa: Purashcharana): Boolean =
        PurashcharanaPath.milestonesFor(sankalpa.mantraAksharaCount)
            .any { it.japaCount == sankalpa.currentJapaCount }
}

/** What a login-time reconcile between the local row and the cloud mirror should do. */
sealed interface PurashcharanaReconcileAction {

    /** Leave Room untouched. The local count is already the truth (or there is nothing to restore). */
    data object None : PurashcharanaReconcileAction

    /**
     * Nothing active locally: adopt the cloud record wholesale. [remote] carries the
     * transport ids (id 0, userId 0) — the caller re-keys it to the local profile.
     */
    data class Restore(val remote: Purashcharana) : PurashcharanaReconcileAction

    /** A local sankalpa exists, but the cloud knows a higher count / an earlier start. */
    data class RaiseLocal(val merged: Purashcharana) : PurashcharanaReconcileAction
}

/**
 * Decides how a local Purashcharana and its cloud mirror are reconciled at login.
 *
 * Pure and clock-free on purpose: this is the one piece of the sync that can lose years
 * of japa if it is wrong, so it must be exhaustively testable without Firestore, Room, or
 * `System.currentTimeMillis()`.
 *
 * The single invariant everything follows from: **a Purashcharana count only ever goes
 * up.** The cloud may raise a local count, never lower it — if the mirror is behind, the
 * next write pushes the local total back up on its own.
 */
object PurashcharanaReconciler {

    /**
     * @param local the ACTIVE local sankalpa, or `null` when the sadhak has none on this
     *   device (never taken one, withdrawn, or wiped by a reinstall).
     * @param remote the mirrored sankalpa, or `null` when there is none / it is unreadable.
     */
    fun resolve(local: Purashcharana?, remote: Purashcharana?): PurashcharanaReconcileAction {
        if (remote == null) return PurashcharanaReconcileAction.None
        // A withdrawn or malformed mirror is not something to restore. Withdrawal is
        // mirrored immediately, so this is how a sankalpa set aside on one device stays
        // set aside after a reinstall.
        if (!remote.isActive) return PurashcharanaReconcileAction.None
        if (remote.targetJapaCount <= 0L ||
            remote.mantraAksharaCount <= 0 ||
            remote.mantraId.isBlank()
        ) {
            return PurashcharanaReconcileAction.None
        }

        if (local == null) return PurashcharanaReconcileAction.Restore(remote)

        // A different mantra, length or target is a DIFFERENT vow, not a stale copy of
        // this one; merging the two counts would fabricate japa. The mantra id is checked
        // first because two mantras can share an akshara count and would otherwise look
        // like the same sankalpa. The local one — the vow the sadhak is actually reciting
        // on this device — wins, and the next mirror overwrites the cloud with it.
        if (remote.mantraId != local.mantraId ||
            remote.mantraAksharaCount != local.mantraAksharaCount ||
            remote.targetJapaCount != local.targetJapaCount
        ) {
            return PurashcharanaReconcileAction.None
        }

        val mergedCount = maxOf(local.currentJapaCount, remote.currentJapaCount)
        // The sankalpa began when it began: keep the earlier start. A missing/zeroed
        // remote start is ignored rather than treated as the epoch.
        val mergedStart = if (remote.startedAtMillis in 1L until local.startedAtMillis) {
            remote.startedAtMillis
        } else {
            local.startedAtMillis
        }
        // Carry the earlier Purnahuti stamp, but only if the merged count actually reaches
        // the target — a stamp without the japa behind it would claim a vow was fulfilled.
        val mergedCompletedAt = listOfNotNull(local.completedAtMillis, remote.completedAtMillis)
            .minOrNull()
            ?.takeIf { mergedCount >= local.targetJapaCount }

        val merged = local.copy(
            currentJapaCount = mergedCount,
            startedAtMillis = mergedStart,
            completedAtMillis = mergedCompletedAt
        )
        return if (merged == local) {
            PurashcharanaReconcileAction.None
        } else {
            PurashcharanaReconcileAction.RaiseLocal(merged)
        }
    }
}
