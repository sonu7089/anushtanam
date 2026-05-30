/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.feedback

import com.mantrasiddhi.domain.evaluation.reference.MantraWord

/**
 * Turns a [SyllableDiff.WordDiff] into a single, presentation-ready [ContrastiveTip]
 * for the most-wrong syllable of a word.
 *
 * Its one job beyond picking the worst syllable is to build the `contrast` hint —
 * e.g. splitting an aspirate so the breath is obvious (`bhar → b-h-ar`) — so the UI
 * template can demonstrate the fix without hardcoding per-word strings.
 */
object FeedbackComposer {

    /** Latin aspirate digraphs whose breath we visually separate in the contrast hint. */
    private val ASPIRATE_DIGRAPHS = listOf("kh", "gh", "ch", "jh", "th", "dh", "ph", "bh")

    /**
     * Composes the top contrastive tip for [referenceWord] given the [spokenWord].
     *
     * @param referenceWord The canonical word (with its syllable breakdown).
     * @param spokenWord What the learner said, or `null`/blank when skipped.
     * @return A [ContrastiveTip] for the weakest syllable, or `null` when the word was
     *   produced accurately enough to need no tip.
     */
    fun compose(referenceWord: MantraWord, spokenWord: String?): ContrastiveTip? {
        val worst = SyllableDiff.diff(referenceWord, spokenWord).worst ?: return null
        return ContrastiveTip(
            word = referenceWord.transliteration,
            syllable = worst.syllable,
            spokenFragment = worst.spokenFragment,
            detail = worst.detail,
            contrast = buildContrast(worst.syllable, worst.feature),
            feature = worst.feature
        )
    }

    /**
     * Builds the correction hint shown to the learner. For aspiration errors the
     * aspirate is split so the breath is visible (`bhar → b-h-ar`); other features
     * just present the correct syllable as the target to imitate.
     */
    private fun buildContrast(syllable: String, feature: FeedbackFeature): String = when (feature) {
        FeedbackFeature.DROPPED_ASPIRATION, FeedbackFeature.ADDED_ASPIRATION -> splitAspirate(syllable)
        else -> syllable
    }

    private fun splitAspirate(syllable: String): String {
        val digraph = ASPIRATE_DIGRAPHS.firstOrNull { syllable.contains(it) } ?: return syllable
        val replacement = "${digraph.first()}-h-"
        return syllable.replaceFirst(digraph, replacement)
    }
}
