/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.feedback.ContrastiveTip
import com.mantrasiddhi.domain.evaluation.feedback.FeedbackComposer
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra

/**
 * Orchestrates actionable, syllable-level pronunciation feedback for a recitation.
 *
 * Phase 4 replaces the old static `WORD_SUGGESTIONS` table (fixed per-word sentences)
 * with [FeedbackComposer]-driven [ContrastiveTip]s computed from the phoneme
 * alignment between the reference word and what the learner actually said. The engine
 * now only *selects* the weak words and orders them; the contrastive content — which
 * syllable, which feature, the correction hint — comes from the diff, and the final
 * localized strings are resolved in the presentation layer (`strings.xml`).
 */
object ImprovementSuggestionEngine {

    /** Per-word score at/below which a word is considered worth a coaching tip. */
    private const val WEAK_WORD_THRESHOLD = 0.85f

    /**
     * Returns the top contrastive tips for the weakest words of a single recitation,
     * worst-first.
     *
     * @param wordResults Per-reference-word results from the evaluation.
     * @param limit Maximum number of tips to return.
     * @return Structured tips naming the syllable/feature to fix, ready for the UI to
     *   localize; empty when every word was produced accurately.
     */
    fun topTips(wordResults: List<WordResult>, limit: Int = 3): List<ContrastiveTip> =
        wordResults
            .filter { it.score < WEAK_WORD_THRESHOLD }
            .sortedBy { it.score }
            .mapNotNull { tipForWordResult(it) }
            .take(limit)

    /**
     * Builds the contrastive tip for a single evaluated word, or `null` when the word
     * is unknown or was produced accurately.
     *
     * @param result The per-word evaluation result (reference + what was spoken).
     */
    fun tipForWordResult(result: WordResult): ContrastiveTip? =
        tipForWord(result.referenceWord, result.spokenWord)

    /**
     * Builds the contrastive tip for a reference word given what the learner said.
     *
     * @param referenceWord The canonical reference word (transliteration).
     * @param spokenWord What the learner said, or `null`/blank when skipped.
     * @return A [ContrastiveTip], or `null` when the word is unknown or accurate.
     */
    fun tipForWord(referenceWord: String, spokenWord: String?): ContrastiveTip? {
        val word = GayatriMantra.wordFor(referenceWord) ?: return null
        return FeedbackComposer.compose(word, spokenWord)
    }
}
