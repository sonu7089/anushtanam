/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.phonetics

/**
 * Feature-weighted phonetic similarity between words, operating on phoneme tokens
 * rather than raw Latin characters.
 *
 * Two improvements over the old char Levenshtein:
 * 1. **Phonetically graded** – substitution cost comes from [PhoneticFeatures], so
 *    `bh→b` (lost aspiration) costs far less than `k→z`.
 * 2. **Length-bias fixed** – the edit cost is normalised by the reference token
 *    count *with a minimum floor*, so short sacred syllables (`om`, `yo`, `nah`)
 *    are not punished disproportionately compared with long words.
 */
object PhoneticDistance {

    /** Insertion/deletion cost of a single phoneme token. */
    private const val INDEL_COST = 1.0

    /**
     * Minimum denominator (in tokens) used when normalising the edit cost.
     *
     * Without a floor, one wrong phoneme in a 2-token word scores `0.5` while the
     * same error in an 11-token word scores `~0.95`. Dividing by at least this many
     * tokens keeps short and long words on a comparable scale.
     */
    private const val MIN_NORMALIZATION_TOKENS = 4.0

    /**
     * Similarity in `0.0..1.0` between an already-tokenized reference and spoken word.
     *
     * @param refTokens Phoneme tokens of the reference word.
     * @param spokenTokens Phoneme tokens of the spoken word.
     */
    fun wordSimilarity(refTokens: List<String>, spokenTokens: List<String>): Double {
        if (refTokens.isEmpty() && spokenTokens.isEmpty()) return 1.0
        if (refTokens.isEmpty() || spokenTokens.isEmpty()) return 0.0
        val cost = weightedEditDistance(refTokens, spokenTokens)
        val denominator = maxOf(refTokens.size.toDouble(), MIN_NORMALIZATION_TOKENS)
        return (1.0 - cost / denominator).coerceIn(0.0, 1.0)
    }

    /**
     * Convenience overload that tokenizes both words via [PhonemeTokenizer] first.
     *
     * @param refWord Reference word (canonical transliteration).
     * @param spokenWord Spoken word (normalized transliteration).
     */
    fun wordSimilarity(refWord: String, spokenWord: String): Double =
        wordSimilarity(PhonemeTokenizer.tokenize(refWord), PhonemeTokenizer.tokenize(spokenWord))

    /** Standard edit-distance DP with feature-weighted substitution cost. */
    private fun weightedEditDistance(ref: List<String>, spoken: List<String>): Double {
        val n = ref.size
        val m = spoken.size
        val dp = DoubleArray(m + 1) { it * INDEL_COST }
        for (i in 1..n) {
            var prev = dp[0]
            dp[0] = i * INDEL_COST
            for (j in 1..m) {
                val temp = dp[j]
                val sub = prev + PhoneticFeatures.substitutionCost(ref[i - 1], spoken[j - 1])
                val del = dp[j] + INDEL_COST
                val ins = dp[j - 1] + INDEL_COST
                dp[j] = minOf(sub, del, ins)
                prev = temp
            }
        }
        return dp[m]
    }
}
