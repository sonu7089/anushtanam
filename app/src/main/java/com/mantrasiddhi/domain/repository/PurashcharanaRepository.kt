/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.Purashcharana
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for the Purashcharana sankalpa (one row per enrolment, kept as history
 * after a withdrawal). At most one row per sadhak is active at a time.
 */
interface PurashcharanaRepository {

    /** Streams the active Purashcharana for [userId], or `null` when there is none. */
    fun observeActive(userId: Long): Flow<Purashcharana?>

    /** One-shot read of the active Purashcharana for [userId]. */
    suspend fun getActive(userId: Long): Purashcharana?

    /**
     * Takes the sankalpa for a mantra of [aksharaCount] aksharas.
     *
     * Fails when a Purashcharana is already active for [userId]: a second concurrent
     * sankalpa would split the sadhak's japa between two counts.
     */
    suspend fun enroll(userId: Long, aksharaCount: Int): Result<Purashcharana>

    /**
     * Adds one verified japa. Returns the updated Purashcharana, or `null` when the
     * sadhak has not taken the sankalpa — that case is a no-op, not an error, because
     * every correct recitation calls this whether or not one is enrolled.
     */
    suspend fun incrementJapa(userId: Long): Result<Purashcharana?>

    /** Withdraws from the sankalpa. The row is deactivated, never deleted. */
    suspend fun withdraw(userId: Long): Result<Unit>

    /**
     * Writes [sankalpa] as the active Purashcharana for [userId], seeding every field —
     * count, start instant, completion stamp — from a cloud record.
     *
     * This exists because [enroll] cannot express a restore: it forces the count to 0 and
     * the start to "now", which is exactly right for a vow being taken and exactly wrong
     * for one being recovered after a reinstall. The active row is updated in place when
     * one exists, so a restore can never leave a sadhak with two active sankalpas.
     *
     * Only the cloud reconcile calls this, and only ever to RAISE a count (see
     * `PurashcharanaReconciler`); there is still no path by which a sadhak can set the
     * number themselves.
     */
    suspend fun adoptRemote(userId: Long, sankalpa: Purashcharana): Result<Unit>
}
