/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Canonicalizes a transliterated word toward its reference spelling.
 *
 * Everything here is a property of **Sanskrit written in Latin letters**, not of any one
 * mantra, so it applies to every mantra the app carries:
 * - long/short vowel folding (`oo→u`, `ee→i`, `aa→a`, doubled vowels)
 * - `w`/`v` confusion
 * - visarga echo (`aha` at the end of a word becomes `ah`, e.g. `svaha → svah`)
 * - spelling variants of the pranava `ॐ` (`aum`, `oom`, …)
 *
 * Genuinely irregular dictation output that no rule can derive is mantra-specific and is
 * therefore supplied by the caller as `overrides`, read from that mantra's definition
 * (`MantraTextRules.spellingOverrides`). Because the phoneme feature-weighted distance
 * tolerates the residual difference, removing any single override does **not** collapse a
 * whole-word match: the rules and the distance still align it.
 */
object PhoneticMap {

    /** Spelling variants of the pranava `ॐ`, resolved before any vowel folding. */
    private val omVariants = setOf("om", "aum", "oom", "aom", "oum", "ohm")

    /**
     * Normalizes a single transliterated word to its canonical reference form.
     *
     * @param word A single transliterated word, in any case.
     * @param overrides Mantra-specific irregular spellings, keyed by either the raw or
     *   the rule-folded form of what was heard.
     * @return The override when one applies, otherwise the rule-folded form.
     */
    fun getCanonical(word: String, overrides: Map<String, String> = emptyMap()): String {
        val trimmed = word.lowercase().trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed in omVariants) return "om"
        val folded = applyPhoneticRules(trimmed)
        return overrides[folded] ?: overrides[trimmed] ?: folded
    }

    /**
     * Applies the rule-based phonetic folds. Exposed for direct rule testing.
     *
     * @param word A lowercased transliterated word.
     * @return The word after vowel, `w` and visarga folding.
     */
    fun applyPhoneticRules(word: String): String {
        var result = word.replace('w', 'v')
        result = foldLongVowels(result)
        result = foldVisargaEcho(result)
        return result
    }

    /** Folds doubled/long vowel spellings onto their short canonical vowel. */
    private fun foldLongVowels(word: String): String = word
        .replace("aa", "a")
        .replace("ee", "i")
        .replace("ii", "i")
        .replace("oo", "u")
        .replace("uu", "u")

    /** Folds the trailing visarga echo `…aha → …ah` (e.g. `svaha → svah`). */
    private fun foldVisargaEcho(word: String): String =
        if (word.length > 2 && word.endsWith("aha")) word.dropLast(1) else word
}
