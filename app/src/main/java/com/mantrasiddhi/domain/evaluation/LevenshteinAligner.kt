/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import kotlin.math.abs

/**
 * Helper class that represents an alignment mapping between reference and spoken words.
 */
data class AlignmentStep(
    val refIndex: Int?,
    val spokenIndex: Int?
)

/**
 * Core alignment engine using Levenshtein distance at both word and character levels.
 */
object LevenshteinAligner {

    private const val EPSILON = 0.0001
    private const val OP_COST_INSERTION = 1.0
    private const val OP_COST_DELETION = 1.0

    /**
     * Calculates the Levenshtein distance between two strings at the character level.
     */
    fun charLevenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = minOf(prev + 1, dp[j] + 1, dp[j - 1] + 1)
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }

    /**
     * Scores the similarity between two words from 0.0 to 1.0.
     */
    fun wordSimilarity(w1: String, w2: String): Double {
        val maxLength = maxOf(w1.length, w2.length)
        if (maxLength == 0) return 1.0
        val dist = charLevenshtein(w1, w2)
        return 1.0 - (dist.toDouble() / maxLength.toDouble())
    }

    /**
     * Aligns a list of spoken words to reference words using Levenshtein alignment.
     */
    fun align(reference: List<String>, spoken: List<String>): List<AlignmentStep> {
        val dp = buildDpMatrix(reference, spoken)
        return backtrack(dp, reference, spoken)
    }

    private fun buildDpMatrix(reference: List<String>, spoken: List<String>): Array<DoubleArray> {
        val n = reference.size
        val m = spoken.size
        val dp = Array(n + 1) { DoubleArray(m + 1) }

        for (i in 0..n) dp[i][0] = i * OP_COST_DELETION
        for (j in 0..m) dp[0][j] = j * OP_COST_INSERTION

        for (i in 1..n) {
            for (j in 1..m) {
                val costSub = 1.0 - wordSimilarity(reference[i - 1], spoken[j - 1])
                val sub = dp[i - 1][j - 1] + costSub
                val del = dp[i - 1][j] + OP_COST_DELETION
                val ins = dp[i][j - 1] + OP_COST_INSERTION
                dp[i][j] = minOf(sub, del, ins)
            }
        }
        return dp
    }

    private fun backtrack(
        dp: Array<DoubleArray>,
        reference: List<String>,
        spoken: List<String>
    ): List<AlignmentStep> {
        val path = mutableListOf<AlignmentStep>()
        var i = reference.size
        var j = spoken.size

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                val costSub = 1.0 - wordSimilarity(reference[i - 1], spoken[j - 1])
                if (abs(dp[i][j] - (dp[i - 1][j - 1] + costSub)) < EPSILON) {
                    path.add(AlignmentStep(i - 1, j - 1))
                    i--
                    j--
                    continue
                }
            }
            if (i > 0 && abs(dp[i][j] - (dp[i - 1][j] + OP_COST_DELETION)) < EPSILON) {
                path.add(AlignmentStep(i - 1, null))
                i--
            } else {
                path.add(AlignmentStep(null, j - 1))
                j--
            }
        }
        return path.reversed()
    }
}
