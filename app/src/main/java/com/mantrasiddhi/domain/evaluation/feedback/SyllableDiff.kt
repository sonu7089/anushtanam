/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.feedback

import com.mantrasiddhi.domain.evaluation.phonetics.PhonemeTokenizer
import com.mantrasiddhi.domain.evaluation.phonetics.PhoneticFeatures
import com.mantrasiddhi.domain.evaluation.reference.MantraWord

/**
 * Produces a **syllable-level diff** between a reference word and what the learner
 * actually said, by aligning their phoneme tokens and attributing each mismatch to
 * the reference syllable it falls in.
 *
 * This is the engine behind contrastive feedback (§4.1): instead of a generic "work
 * on this word", it identifies *which syllable* was wrong and *which feature*
 * changed (dropped aspiration, dental↔retroflex swap, shortened long vowel, missing
 * visarga echo, …) so [FeedbackComposer] can phrase a targeted tip.
 *
 * It is pure (operates on phoneme tokens) and therefore fully unit-testable.
 */
object SyllableDiff {

    /** Insertion/deletion cost of a single phoneme token (mirrors PhoneticDistance). */
    private const val INDEL_COST = 1.0

    /**
     * Cost above which a syllable is worth flagging in feedback. Set just below the
     * smallest single-feature slip (dropped aspiration / vowel length both cost 0.15)
     * so those *are* surfaced, while exact matches (cost 0.0) are ignored.
     */
    private const val MISMATCH_EPSILON = 0.05

    /** One reference syllable that was not produced accurately. */
    data class SyllableMismatch(
        val syllableIndex: Int,
        val syllable: String,
        val feature: FeedbackFeature,
        val detail: String,
        val spokenFragment: String,
        val cost: Double
    )

    /** The full diff for a word: mismatched syllables ordered worst-first. */
    data class WordDiff(
        val word: String,
        val mismatches: List<SyllableMismatch>
    ) {
        /** The single most-wrong syllable, or `null` when the word is accurate. */
        val worst: SyllableMismatch? get() = mismatches.firstOrNull()
    }

    /**
     * Diffs the [spokenWord] against [referenceWord], localising errors to syllables.
     *
     * @param referenceWord The canonical word, carrying its syllable breakdown.
     * @param spokenWord What the learner said, or `null`/blank when the word was skipped.
     * @return A [WordDiff] with mismatched syllables sorted by severity (worst first);
     *   empty mismatches mean the word was produced accurately.
     */
    fun diff(referenceWord: MantraWord, spokenWord: String?): WordDiff {
        val syllables = referenceWord.syllables.ifEmpty { listOf(referenceWord.transliteration) }
        if (spokenWord.isNullOrBlank()) {
            return WordDiff(referenceWord.transliteration, listOf(missingWordMismatch(syllables)))
        }
        val refTokens = flattenSyllableTokens(referenceWord.transliteration, syllables)
        if (refTokens.isEmpty()) return WordDiff(referenceWord.transliteration, emptyList())
        val spokenTokens = PhonemeTokenizer.tokenize(spokenWord)
        val ops = align(refTokens.map { it.token }, spokenTokens)
        val mismatches = collectMismatches(refTokens, spokenTokens, ops, syllables)
        return WordDiff(referenceWord.transliteration, mismatches)
    }

    /** A reference phoneme token tagged with the syllable index it belongs to. */
    private data class RefToken(val token: String, val syllableIndex: Int, val isLast: Boolean)

    /** A single alignment operation between the reference and spoken token streams. */
    private data class Op(val refIndex: Int?, val spokenIndex: Int?, val cost: Double)

    private fun flattenSyllableTokens(word: String, syllables: List<String>): List<RefToken> {
        val groups = PhonemeTokenizer.tokenizeSyllables(word, syllables)
        val result = mutableListOf<RefToken>()
        groups.forEachIndexed { syllableIndex, tokens ->
            tokens.forEach { result += RefToken(it, syllableIndex, isLast = false) }
        }
        return if (result.isEmpty()) result else result.dropLast(1) + result.last().copy(isLast = true)
    }

    /** Needleman–Wunsch alignment with feature-weighted substitution cost and traceback. */
    private fun align(ref: List<String>, spoken: List<String>): List<Op> {
        val n = ref.size
        val m = spoken.size
        val dp = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 1..n) dp[i][0] = i * INDEL_COST
        for (j in 1..m) dp[0][j] = j * INDEL_COST
        for (i in 1..n) {
            for (j in 1..m) {
                val sub = dp[i - 1][j - 1] + PhoneticFeatures.substitutionCost(ref[i - 1], spoken[j - 1])
                val del = dp[i - 1][j] + INDEL_COST
                val ins = dp[i][j - 1] + INDEL_COST
                dp[i][j] = minOf(sub, del, ins)
            }
        }
        return traceback(ref, spoken, dp)
    }

    private fun traceback(ref: List<String>, spoken: List<String>, dp: Array<DoubleArray>): List<Op> {
        val ops = ArrayDeque<Op>()
        var i = ref.size
        var j = spoken.size
        while (i > 0 || j > 0) {
            val subCost = if (i > 0 && j > 0) PhoneticFeatures.substitutionCost(ref[i - 1], spoken[j - 1]) else Double.MAX_VALUE
            when {
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + subCost -> {
                    ops.addFirst(Op(i - 1, j - 1, subCost)); i--; j--
                }
                i > 0 && dp[i][j] == dp[i - 1][j] + INDEL_COST -> {
                    ops.addFirst(Op(i - 1, null, INDEL_COST)); i--
                }
                else -> {
                    ops.addFirst(Op(null, j - 1, INDEL_COST)); j--
                }
            }
        }
        return ops.toList()
    }

    private fun collectMismatches(
        refTokens: List<RefToken>,
        spokenTokens: List<String>,
        ops: List<Op>,
        syllables: List<String>
    ): List<SyllableMismatch> {
        val cost = DoubleArray(syllables.size)
        val spokenBySyllable = Array(syllables.size) { mutableListOf<String>() }
        val worstOp = arrayOfNulls<Op>(syllables.size)
        var lastSyllable = 0

        for (op in ops) {
            val syllableIndex = op.refIndex?.let { refTokens[it].syllableIndex } ?: lastSyllable
            lastSyllable = syllableIndex
            cost[syllableIndex] += op.cost
            op.spokenIndex?.let { spokenBySyllable[syllableIndex] += spokenTokens[it] }
            if (op.cost > MISMATCH_EPSILON) {
                val current = worstOp[syllableIndex]
                if (current == null || op.cost > current.cost) worstOp[syllableIndex] = op
            }
        }

        return syllables.indices
            .filter { cost[it] > MISMATCH_EPSILON && worstOp[it] != null }
            .map { idx ->
                buildMismatch(idx, syllables[idx], refTokens, spokenTokens, worstOp[idx]!!, spokenBySyllable[idx], cost[idx])
            }
            .sortedByDescending { it.cost }
    }

    private fun buildMismatch(
        syllableIndex: Int,
        syllable: String,
        refTokens: List<RefToken>,
        spokenTokens: List<String>,
        op: Op,
        spokenFragmentTokens: List<String>,
        cost: Double
    ): SyllableMismatch {
        val refToken = op.refIndex?.let { refTokens[it] }
        val spokenToken = op.spokenIndex?.let { spokenTokens[it] }
        val feature = classify(refToken, spokenToken)
        val detail = refToken?.token ?: spokenToken ?: syllable
        val fragment = spokenFragmentTokens.joinToString("").ifEmpty { "—" }
        return SyllableMismatch(syllableIndex, syllable, feature, detail, fragment, cost)
    }

    private fun classify(refToken: RefToken?, spokenToken: String?): FeedbackFeature {
        // Deletion: a reference sound the learner did not produce.
        if (refToken != null && spokenToken == null) return deletionFeature(refToken)
        // Insertion only: an extra sound with no clear reference counterpart.
        if (refToken == null) return FeedbackFeature.GENERIC
        // Substitution: name the feature that changed.
        return when (PhoneticFeatures.contrast(refToken.token, spokenToken!!)) {
            PhoneticFeatures.Contrast.DROPPED_ASPIRATION -> FeedbackFeature.DROPPED_ASPIRATION
            PhoneticFeatures.Contrast.ADDED_ASPIRATION -> FeedbackFeature.ADDED_ASPIRATION
            PhoneticFeatures.Contrast.DENTAL_FOR_RETROFLEX -> FeedbackFeature.DENTAL_FOR_RETROFLEX
            PhoneticFeatures.Contrast.RETROFLEX_FOR_DENTAL -> FeedbackFeature.RETROFLEX_FOR_DENTAL
            PhoneticFeatures.Contrast.SHORTENED_VOWEL -> FeedbackFeature.SHORTENED_VOWEL
            PhoneticFeatures.Contrast.LENGTHENED_VOWEL -> FeedbackFeature.LENGTHENED_VOWEL
            PhoneticFeatures.Contrast.VOWEL_QUALITY -> FeedbackFeature.VOWEL_QUALITY
            PhoneticFeatures.Contrast.VOICING -> FeedbackFeature.VOICING
            PhoneticFeatures.Contrast.PLACE_SHIFT,
            PhoneticFeatures.Contrast.MANNER_SHIFT -> FeedbackFeature.PLACE_OR_MANNER
            else -> FeedbackFeature.GENERIC
        }
    }

    private fun deletionFeature(refToken: RefToken): FeedbackFeature = when {
        PhoneticFeatures.isVisarga(refToken.token) -> FeedbackFeature.MISSING_VISARGA
        refToken.isLast && refToken.token == "h" -> FeedbackFeature.MISSING_VISARGA
        PhoneticFeatures.isNasal(refToken.token) -> FeedbackFeature.MISSING_NASAL
        else -> FeedbackFeature.MISSING_SOUND
    }

    private fun missingWordMismatch(syllables: List<String>): SyllableMismatch =
        SyllableMismatch(
            syllableIndex = 0,
            syllable = syllables.first(),
            feature = FeedbackFeature.MISSING_WORD,
            detail = syllables.first(),
            spokenFragment = "—",
            cost = Double.MAX_VALUE
        )
}
