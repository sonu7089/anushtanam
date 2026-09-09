package com.mantrasiddhi.domain.model

import java.math.BigInteger

/** Performed quantities are never capped or multiplied by the number of goals. */
object JapaAccounting {
    fun credited(baseline: Long, quantities: List<Long>, target: Long): Long {
        require(baseline >= 0 && target >= 0 && quantities.all { it >= 0 })
        return quantities.fold(BigInteger.valueOf(baseline)) { sum, n -> sum + BigInteger.valueOf(n) }
            .min(BigInteger.valueOf(target)).longValueExact()
    }

    fun crossed(before: Long, after: Long, aksharas: Int): List<PurashcharanaStage> =
        PurashcharanaPath.milestonesFor(aksharas)
            .filter { before < it.japaCount && it.japaCount <= after }.map { it.stage }
}

data class JapaGoalFeedback(
    val targetCompleted: Boolean = false,
    val purashcharanaCompleted: Boolean = false,
    val completionReversed: Boolean = false,
    val milestonesCrossed: Int = 0
)
