/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.domain.model.PurashcharanaPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The reconcile is the one place in the Purashcharana sync that can destroy years of
 * japa, so every branch is pinned here. No assertion reads the clock: a sankalpa spans
 * years, and a test that depends on "now" cannot state what it means.
 */
class PurashcharanaSyncReconcileTest {

    private val day = 24L * 60L * 60L * 1000L

    /** Fixed instants: `earlier` is the day the sankalpa was truly taken. */
    private val earlier = 1_700_000_000_000L
    private val later = earlier + 30L * day

    private val gayatriTarget = PurashcharanaPath.targetFor(24)

    private fun local(
        current: Long = 0L,
        startedAtMillis: Long = later,
        completedAtMillis: Long? = null,
        aksharas: Int = 24,
        target: Long = gayatriTarget,
        mantraId: String = "gayatri"
    ) = Purashcharana(
        id = 5L,
        userId = 7L,
        mantraId = mantraId,
        mantraAksharaCount = aksharas,
        targetJapaCount = target,
        currentJapaCount = current,
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        isActive = true
    )

    /** A mirrored record as PurashcharanaSyncRepository returns it: local ids zeroed. */
    private fun remote(
        current: Long = 0L,
        startedAtMillis: Long = earlier,
        completedAtMillis: Long? = null,
        aksharas: Int = 24,
        target: Long = gayatriTarget,
        isActive: Boolean = true,
        mantraId: String = "gayatri"
    ) = Purashcharana(
        id = 0L,
        userId = 0L,
        mantraId = mantraId,
        mantraAksharaCount = aksharas,
        targetJapaCount = target,
        currentJapaCount = current,
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        isActive = isActive
    )

    private fun resolve(local: Purashcharana?, remote: Purashcharana?) =
        PurashcharanaReconciler.resolve(local, remote)

    // --- nothing to reconcile ---------------------------------------------------

    @Test
    fun `no local and no remote does nothing`() {
        assertEquals(PurashcharanaReconcileAction.None, resolve(null, null))
    }

    @Test
    fun `a local sankalpa with no remote is left alone`() {
        // The mirror is simply behind (never signed in, first run offline). The next
        // mirror pushes the local count up; the cloud must not be allowed to speak here.
        assertEquals(PurashcharanaReconcileAction.None, resolve(local(current = 12_000L), null))
    }

    // --- restore ----------------------------------------------------------------

    @Test
    fun `no local sankalpa restores the remote wholesale`() {
        val mirrored = remote(current = 431_000L, completedAtMillis = null)
        val action = resolve(null, mirrored)

        val restored = (action as PurashcharanaReconcileAction.Restore).remote
        // Passed through untouched: the caller re-keys it to the local profile.
        assertSame(mirrored, restored)
        assertEquals(431_000L, restored.currentJapaCount)
        assertEquals(earlier, restored.startedAtMillis)
        assertEquals(24, restored.mantraAksharaCount)
        assertEquals(gayatriTarget, restored.targetJapaCount)
    }

    @Test
    fun `a completed remote is restored with its purnahuti stamp`() {
        val done = remote(current = gayatriTarget, completedAtMillis = later)
        val restored = (resolve(null, done) as PurashcharanaReconcileAction.Restore).remote

        assertEquals(later, restored.completedAtMillis)
        assertTrue(restored.isCompleted)
    }

    @Test
    fun `a withdrawn remote is never resurrected`() {
        // Withdrawal is mirrored immediately, so an inactive doc means the sadhak set the
        // sankalpa aside — a reinstall must not hand it back to them.
        val withdrawn = remote(current = 90_000L, isActive = false)
        assertEquals(PurashcharanaReconcileAction.None, resolve(null, withdrawn))
        assertEquals(PurashcharanaReconcileAction.None, resolve(local(current = 10L), withdrawn))
    }

    @Test
    fun `a malformed remote is ignored`() {
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(null, remote(current = 5L, target = 0L))
        )
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(null, remote(current = 5L, aksharas = 0))
        )
    }

    // --- monotonic raise --------------------------------------------------------

    @Test
    fun `a higher remote count raises the local count`() {
        // The reinstall replayed some japa locally; the cloud remembers the real total.
        val action = resolve(local(current = 1_200L), remote(current = 240_000L))

        val merged = (action as PurashcharanaReconcileAction.RaiseLocal).merged
        assertEquals(240_000L, merged.currentJapaCount)
        // The local row identity is kept, so the restore updates it rather than adding a
        // second active sankalpa.
        assertEquals(5L, merged.id)
        assertEquals(7L, merged.userId)
    }

    @Test
    fun `a lower remote count never lowers the local count`() {
        // The throttle means the mirror is routinely a little behind. That must be a
        // no-op, never a rollback.
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(local(current = 240_500L, startedAtMillis = earlier), remote(current = 240_000L))
        )
    }

    @Test
    fun `equal counts and equal starts do nothing`() {
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(
                local(current = 100_000L, startedAtMillis = earlier),
                remote(current = 100_000L, startedAtMillis = earlier)
            )
        )
    }

    @Test
    fun `the earlier start wins when the remote began first`() {
        // A reinstall re-enrols "today"; the vow itself began weeks ago, and day numbers
        // and pace must be reckoned from then.
        val action = resolve(
            local(current = 500L, startedAtMillis = later),
            remote(current = 400L, startedAtMillis = earlier)
        )

        val merged = (action as PurashcharanaReconcileAction.RaiseLocal).merged
        assertEquals(earlier, merged.startedAtMillis)
        // The lower remote count still did not touch the local one.
        assertEquals(500L, merged.currentJapaCount)
    }

    @Test
    fun `a later remote start is ignored`() {
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(
                local(current = 500L, startedAtMillis = earlier),
                remote(current = 500L, startedAtMillis = later)
            )
        )
    }

    @Test
    fun `a zeroed remote start is not treated as the epoch`() {
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(
                local(current = 500L, startedAtMillis = earlier),
                remote(current = 500L, startedAtMillis = 0L)
            )
        )
    }

    @Test
    fun `a higher count and an earlier start are merged together`() {
        val action = resolve(
            local(current = 1_000L, startedAtMillis = later),
            remote(current = 600_000L, startedAtMillis = earlier)
        )

        val merged = (action as PurashcharanaReconcileAction.RaiseLocal).merged
        assertEquals(600_000L, merged.currentJapaCount)
        assertEquals(earlier, merged.startedAtMillis)
    }

    // --- completion stamps ------------------------------------------------------

    @Test
    fun `a remote purnahuti completes a local sankalpa that lost its last japa`() {
        val action = resolve(
            local(current = gayatriTarget - 40L),
            remote(current = gayatriTarget, completedAtMillis = later)
        )

        val merged = (action as PurashcharanaReconcileAction.RaiseLocal).merged
        assertEquals(gayatriTarget, merged.currentJapaCount)
        assertEquals(later, merged.completedAtMillis)
        assertTrue(merged.isCompleted)
    }

    @Test
    fun `a remote completion stamp without the japa behind it is dropped`() {
        // Defensive: a stamp on a doc whose count is short of the target would otherwise
        // claim a vow was fulfilled that was not.
        val action = resolve(
            local(current = 10_000L, startedAtMillis = later),
            remote(current = 20_000L, startedAtMillis = earlier, completedAtMillis = later)
        )

        val merged = (action as PurashcharanaReconcileAction.RaiseLocal).merged
        assertNull(merged.completedAtMillis)
        assertFalse(merged.isCompleted)
    }

    @Test
    fun `the earlier purnahuti stamp is kept when both sides completed`() {
        val action = resolve(
            local(current = gayatriTarget, startedAtMillis = later, completedAtMillis = later + day),
            remote(current = gayatriTarget, startedAtMillis = earlier, completedAtMillis = later)
        )

        val merged = (action as PurashcharanaReconcileAction.RaiseLocal).merged
        assertEquals(later, merged.completedAtMillis)
    }

    @Test
    fun `a local completion is not undone by an incomplete remote`() {
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(
                local(current = gayatriTarget, startedAtMillis = earlier, completedAtMillis = later),
                remote(current = gayatriTarget, startedAtMillis = earlier, completedAtMillis = null)
            )
        )
    }

    // --- a different sankalpa ---------------------------------------------------

    @Test
    fun `a remote for a different mantra length is not merged in`() {
        // A different akshara count is a different vow, not a stale copy of this one;
        // summing the two counts would fabricate japa. The local sankalpa wins and the
        // next mirror overwrites the cloud with it.
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(
                local(current = 1_000L),
                remote(current = 900_000L, aksharas = 8, target = PurashcharanaPath.targetFor(8))
            )
        )
    }

    @Test
    fun `a remote for a different mantra is not merged in even at the same length`() {
        // Two mantras can share an akshara count and therefore a target, so the akshara
        // check alone would let one vow's japa be poured into another's.
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(
                local(current = 1_000L, mantraId = "gayatri"),
                remote(current = 900_000L, mantraId = "mahamrityunjaya")
            )
        )
    }

    @Test
    fun `a remote with no mantra recorded is ignored rather than guessed at`() {
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(local(current = 1_000L), remote(current = 900_000L, mantraId = "  "))
        )
    }

    @Test
    fun `a remote with a different target is not merged in even at the same length`() {
        assertEquals(
            PurashcharanaReconcileAction.None,
            resolve(local(current = 1_000L), remote(current = 900_000L, target = 1_000_000L))
        )
    }

    @Test
    fun `a remote for a different sankalpa is still restored when there is nothing local`() {
        // Nothing local can be contradicted, so the record is honoured on its own terms.
        val eight = remote(current = 700_000L, aksharas = 8, target = PurashcharanaPath.targetFor(8))
        val restored = (resolve(null, eight) as PurashcharanaReconcileAction.Restore).remote

        assertEquals(8, restored.mantraAksharaCount)
        assertEquals(700_000L, restored.currentJapaCount)
    }

    // --- immediate-mirror policy ------------------------------------------------

    @Test
    fun `a count landing exactly on a milestone forces an immediate mirror`() {
        assertTrue(PurashcharanaMirrorPolicy.landsOnMilestone(local(current = 1_008L)))
        assertTrue(PurashcharanaMirrorPolicy.landsOnMilestone(local(current = 100_000L)))
        // The Purnahuti is the last milestone, so completion is covered by the same rule.
        assertTrue(PurashcharanaMirrorPolicy.landsOnMilestone(local(current = gayatriTarget)))
    }

    @Test
    fun `an ordinary japa is left to the throttle`() {
        assertFalse(PurashcharanaMirrorPolicy.landsOnMilestone(local(current = 0L)))
        assertFalse(PurashcharanaMirrorPolicy.landsOnMilestone(local(current = 1_007L)))
        assertFalse(PurashcharanaMirrorPolicy.landsOnMilestone(local(current = 1_009L)))
    }
}
