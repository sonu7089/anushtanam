/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PurashcharanaTest {

    private val day = 24L * 60L * 60L * 1000L

    /** Fixed start instant: a sankalpa spans years, so no assertion may read the clock. */
    private val start = 1_700_000_000_000L

    private fun sankalpa(
        aksharas: Int = 24,
        target: Long = PurashcharanaPath.targetFor(aksharas),
        current: Long = 0L,
        startedAtMillis: Long = start,
        completedAtMillis: Long? = null,
        isActive: Boolean = true
    ) = Purashcharana(
        id = 1L,
        userId = 7L,
        mantraId = "gayatri",
        mantraAksharaCount = aksharas,
        targetJapaCount = target,
        currentJapaCount = current,
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        isActive = isActive
    )

    // --- targetFor -------------------------------------------------------------

    @Test
    fun `targetFor is one lakh japa per akshara`() {
        assertEquals(2_400_000L, PurashcharanaPath.targetFor(24))
        assertEquals(100_000L, PurashcharanaPath.targetFor(1))
        assertEquals(800_000L, PurashcharanaPath.targetFor(8))
    }

    @Test
    fun `targetFor is zero for a mantra with no aksharas`() {
        assertEquals(0L, PurashcharanaPath.targetFor(0))
        assertEquals(0L, PurashcharanaPath.targetFor(-5))
    }

    // --- milestone ladder ------------------------------------------------------

    @Test
    fun `ladder for the Gayatri has all ten stages in order`() {
        val ladder = PurashcharanaPath.milestonesFor(24)
        assertEquals(
            listOf(
                1_008L, 10_000L, 24_000L, 100_000L, 240_000L,
                600_000L, 1_200_000L, 1_800_000L, 2_160_000L, 2_400_000L
            ),
            ladder.map { it.japaCount }
        )
        assertEquals(PurashcharanaStage.entries.toList(), ladder.map { it.stage })
        assertEquals(List(10) { it }, ladder.map { it.ordinal })
    }

    @Test
    fun `ladder drops early stages that a short mantra would push past the tenth`() {
        // 8 aksharas -> 8,00,000 japa, so the tenth lands at 80,000: eka laksha
        // (1,00,000) sits past it and must be dropped, and sahasra avritti (8,000)
        // now comes before dasha sahasra (10,000).
        val ladder = PurashcharanaPath.milestonesFor(8)
        assertEquals(
            listOf(1_008L, 8_000L, 10_000L, 80_000L, 200_000L, 400_000L, 600_000L, 720_000L, 800_000L),
            ladder.map { it.japaCount }
        )
        assertEquals(
            listOf(
                PurashcharanaStage.ARAMBHA,
                PurashcharanaStage.SAHASRA_AVRITTI,
                PurashcharanaStage.DASHA_SAHASRA,
                PurashcharanaStage.DASHAMSHA,
                PurashcharanaStage.CHATURTHAMSHA,
                PurashcharanaStage.ARDHA,
                PurashcharanaStage.TRIPADA,
                PurashcharanaStage.ANTIMA,
                PurashcharanaStage.PURNAHUTI
            ),
            ladder.map { it.stage }
        )
        assertEquals(List(9) { it }, ladder.map { it.ordinal })
    }

    @Test
    fun `ladder de-duplicates early stages that land on the same count`() {
        // 10 aksharas: sahasra avritti (10 x 1,000) collides with dasha sahasra.
        val ladder = PurashcharanaPath.milestonesFor(10)
        assertEquals(ladder.map { it.japaCount }.distinct(), ladder.map { it.japaCount })
        assertEquals(1, ladder.count { it.japaCount == 10_000L })
        assertEquals(PurashcharanaStage.DASHA_SAHASRA, ladder.first { it.japaCount == 10_000L }.stage)
    }

    @Test
    fun `ladder is always strictly increasing and ends at the target`() {
        listOf(1, 2, 8, 10, 24, 32).forEach { aksharas ->
            val ladder = PurashcharanaPath.milestonesFor(aksharas)
            val counts = ladder.map { it.japaCount }
            assertEquals(counts.sorted(), counts, "ladder must ascend for $aksharas aksharas")
            assertEquals(counts.distinct(), counts, "ladder must not repeat for $aksharas aksharas")
            assertEquals(PurashcharanaPath.targetFor(aksharas), counts.last())
            assertEquals(PurashcharanaStage.PURNAHUTI, ladder.last().stage)
        }
    }

    @Test
    fun `ladder is empty when there is nothing to recite`() {
        assertTrue(PurashcharanaPath.milestonesFor(0).isEmpty())
    }

    // --- progress, remaining, completion ---------------------------------------

    @Test
    fun `progress is the fraction of the sankalpa recited`() {
        assertEquals(0.25f, sankalpa(current = 600_000L).progress)
        assertEquals(0f, sankalpa(current = 0L).progress)
    }

    @Test
    fun `progress is coerced into zero to one`() {
        // A count past the target (should be impossible) still renders as a full bar.
        assertEquals(1f, sankalpa(current = 3_000_000L).progress)
        assertEquals(0f, sankalpa(target = 0L, current = 10L).progress)
    }

    @Test
    fun `remaining japa never goes negative`() {
        assertEquals(2_399_000L, sankalpa(current = 1_000L).remainingJapaCount)
        assertEquals(0L, sankalpa(current = 2_400_000L).remainingJapaCount)
        assertEquals(0L, sankalpa(current = 2_500_000L).remainingJapaCount)
    }

    @Test
    fun `isCompleted when the count reaches the target`() {
        assertFalse(sankalpa(current = 2_399_999L).isCompleted)
        assertTrue(sankalpa(current = 2_400_000L).isCompleted)
    }

    @Test
    fun `isCompleted when a completion stamp exists even without the count`() {
        // Defensive: the Purnahuti is recognised even if the stamp and the count
        // ever disagree.
        assertTrue(sankalpa(current = 10L, completedAtMillis = start + 900L * day).isCompleted)
    }

    // --- dayNumber -------------------------------------------------------------

    @Test
    fun `dayNumber counts from one on the day the sankalpa was taken`() {
        assertEquals(1, sankalpa().dayNumber(start))
        assertEquals(1, sankalpa().dayNumber(start + day - 1L))
        assertEquals(2, sankalpa().dayNumber(start + day))
        assertEquals(10, sankalpa().dayNumber(start + 9L * day))
        assertEquals(366, sankalpa().dayNumber(start + 365L * day))
    }

    @Test
    fun `dayNumber clamps to one when the clock has moved backwards`() {
        assertEquals(1, sankalpa().dayNumber(start - 5L * day))
    }

    // --- milestones against a live count ---------------------------------------

    @Test
    fun `nothing is reached and arambha is next on day one`() {
        val fresh = sankalpa()
        assertTrue(fresh.reachedMilestones().isEmpty())
        assertEquals(PurashcharanaStage.ARAMBHA, fresh.nextMilestone()?.stage)
    }

    @Test
    fun `a milestone is reached exactly on its count`() {
        val atLaksha = sankalpa(current = 100_000L)
        assertEquals(
            listOf(
                PurashcharanaStage.ARAMBHA,
                PurashcharanaStage.DASHA_SAHASRA,
                PurashcharanaStage.SAHASRA_AVRITTI,
                PurashcharanaStage.EKA_LAKSHA
            ),
            atLaksha.reachedMilestones().map { it.stage }
        )
        assertEquals(PurashcharanaStage.DASHAMSHA, atLaksha.nextMilestone()?.stage)
        assertEquals(240_000L, atLaksha.nextMilestone()?.japaCount)
    }

    @Test
    fun `no milestone is left after purnahuti`() {
        val done = sankalpa(current = 2_400_000L)
        assertEquals(10, done.reachedMilestones().size)
        assertNull(done.nextMilestone())
    }

    // --- pace and projection ---------------------------------------------------

    @Test
    fun `japaPerDay averages the verified japa over the elapsed days`() {
        // 300 japa by day 3 -> 100 a day.
        assertEquals(100.0, sankalpa(current = 300L).japaPerDay(start + 2L * day)!!, 1e-9)
    }

    @Test
    fun `japaPerDay on day one is the count itself`() {
        // Not null: the "no pace yet" copy is reserved for a sadhak who has not been
        // heard reciting at all, and would read as a contradiction here.
        assertEquals(50.0, sankalpa(current = 50L).japaPerDay(start)!!, 1e-9)
    }

    @Test
    fun `japaPerDay is null when no japa has been verified yet`() {
        assertNull(sankalpa(current = 0L).japaPerDay(start))
        assertNull(sankalpa(current = 0L).japaPerDay(start + 30L * day))
    }

    @Test
    fun `projectedDaysRemaining divides what is left by the pace`() {
        // 300 japa by day 3 -> 100 a day; 23,99,700 left -> 23,997 days.
        assertEquals(23_997L, sankalpa(current = 300L).projectedDaysRemaining(start + 2L * day))
    }

    @Test
    fun `projectedDaysRemaining rounds partial days up`() {
        // 7 japa by day 2 -> 3.5 a day; 13 left -> 3.71 days -> 4.
        val slow = sankalpa(target = 20L, current = 7L)
        assertEquals(4L, slow.projectedDaysRemaining(start + day))
    }

    @Test
    fun `projectedDaysRemaining is null when there is no pace yet`() {
        assertNull(sankalpa(current = 0L).projectedDaysRemaining(start))
    }

    @Test
    fun `projectedDaysRemaining is null once the sankalpa is complete`() {
        assertNull(sankalpa(current = 2_400_000L).projectedDaysRemaining(start + 900L * day))
    }
}
