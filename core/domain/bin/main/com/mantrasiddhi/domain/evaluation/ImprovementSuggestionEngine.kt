/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.feedback.ContrastiveTip
import com.mantrasiddhi.domain.evaluation.feedback.FeedbackComposer
import com.mantrasiddhi.domain.mantra.BundledMantraCatalog
import com.mantrasiddhi.domain.mantra.Mantra
import com.mantrasiddhi.domain.mantra.MantraCatalog

/**
 * Orchestrates actionable, syllable-level pronunciation feedback for a recitation.
 *
 * The engine only *selects* the weak words and orders them; the contrastive content —
 * which syllable, which feature, the correction hint — comes from the phoneme diff in
 * [FeedbackComposer], and the final localized strings are resolved in the presentation
 * layer (`strings.xml`).
 */
object ImprovementSuggestionEngine {

    /** Per-word score at/below which a word is considered worth a coaching tip. */
    private const val WEAK_WORD_THRESHOLD = 0.85f

    /** Default number of tips a single recitation is worth. */
    private const val DEFAULT_TIP_LIMIT = 3

    /**
     * Returns the top contrastive tips for the weakest words of a single recitation,
     * worst-first.
     *
     * @param mantra The mantra recited; supplies each word's syllable breakdown.
     * @param wordResults Per-reference-word results from the evaluation.
     * @param limit Maximum number of tips to return.
     * @return Structured tips naming the syllable/feature to fix, ready for the UI to
     *   localize; empty when every word was produced accurately.
     */
    fun topTips(
        mantra: Mantra,
        wordResults: List<WordResult>,
        limit: Int = DEFAULT_TIP_LIMIT
    ): List<ContrastiveTip> =
        wordResults
            .filter { it.score < WEAK_WORD_THRESHOLD }
            .sortedBy { it.score }
            .mapNotNull { tipForWordResult(mantra, it) }
            .take(limit)

    /**
     * Builds the contrastive tip for a single evaluated word, or `null` when the word is
     * not part of [mantra] or was produced accurately.
     */
    fun tipForWordResult(mantra: Mantra, result: WordResult): ContrastiveTip? =
        mantra.wordFor(result.referenceWord)?.let { FeedbackComposer.compose(it, result.spokenWord) }

    /**
     * Builds the contrastive tip for a reference word given what the learner said, looking
     * the word up across the bundled catalog.
     *
     * This exists for callers that hold a stored transliteration but not the mantra it
     * belongs to — the presentation layer resolving feedback strings for a past session.
     * Prefer [tipForWordResult] whenever the mantra is known.
     *
     * @param referenceWord The canonical word (transliteration).
     * @param spokenWord What the learner said, or `null`/blank when skipped.
     * @param catalog Where to look the word up; defaults to the bundled catalog.
     * @return A [ContrastiveTip], or `null` when the word is unknown or accurate.
     */
    fun tipForWord(
        referenceWord: String,
        spokenWord: String?,
        catalog: MantraCatalog = BundledMantraCatalog
    ): ContrastiveTip? =
        catalog.wordFor(referenceWord)?.let { FeedbackComposer.compose(it, spokenWord) }
}
