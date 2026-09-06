/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.mantra.MantraTextRules
import java.text.Normalizer

/**
 * Turns raw speech-to-text output into the whitespace-separated, canonically spelled
 * Latin text the evaluator compares against a mantra's reference words.
 *
 * The pipeline is split in two on purpose. Steps 1-5 and the per-word canonicalization are
 * true of Sanskrit in general; only [MantraTextRules] is specific to one mantra, and it
 * arrives as a parameter rather than a hardcoded table, so a new mantra brings its own
 * dictation quirks with it in its definition file.
 */
object TextNormalizer {

    /** Punctuation (Latin and Devanagari) that carries no phonetic weight. */
    private val PUNCTUATION = Regex("""[.,/#!$%^&*;:{}=\-_`~()?'"।॥]""")

    private val WHITESPACE = Regex("""\s+""")

    /**
     * Normalizes [text] for word-level comparison by:
     * 1. normalizing Unicode to NFC,
     * 2. transliterating Devanagari to Latin,
     * 3. lowercasing,
     * 4. replacing punctuation with spaces and collapsing whitespace,
     * 5. applying the mantra's compound splits/joins, then
     * 6. canonicalizing each word with [PhoneticMap].
     *
     * @param text The raw transcription or reference text.
     * @param rules The mantra's own normalization rules; defaults to the general rules
     *   alone, which is what script-level tests and mantra-agnostic callers want.
     * @return Normalized text, or the empty string when nothing survives normalization.
     */
    fun normalize(text: String, rules: MantraTextRules = MantraTextRules.NONE): String {
        if (text.isBlank()) return ""

        val transliterated = DevanagariTransliterator.transliterate(
            Normalizer.normalize(text, Normalizer.Form.NFC)
        )
        val cleaned = transliterated
            .lowercase()
            .replace(PUNCTUATION, " ")
            .replace(WHITESPACE, " ")
            .trim()
        if (cleaned.isEmpty()) return ""

        val split = applyCompoundReplacements(cleaned, rules)
        return split.split(" ")
            .joinToString(" ") { PhoneticMap.getCanonical(it, rules.spellingOverrides) }
    }

    /**
     * Applies the mantra's compound replacements in order.
     *
     * Order is part of the data: an earlier replacement may produce exactly the text a
     * later one matches, and a longer compound must be listed before any prefix of it.
     */
    private fun applyCompoundReplacements(text: String, rules: MantraTextRules): String =
        rules.compoundReplacements.fold(text) { acc, replacement ->
            acc.replace(replacement.from, replacement.to)
        }
}
