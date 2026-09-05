/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Canonicalizes a transliterated word toward its reference spelling.
 *
 * Phase 2 replaces most of the old hand-listed STT-misrecognition table with
 * **rules** derived from how Sanskrit is commonly mis-spelled by a dictation
 * engine:
 * - long↔short vowel folding (`oo→u`, `ee→i`, `aa→a`, doubled vowels)
 * - `w↔v` confusion
 * - visarga echo (`…aha → …ah`, e.g. `svaha → svah`)
 * - `om` spelling variants (`aum`, `oom`, …)
 *
 * Only genuinely irregular outputs that no rule can derive remain in the small,
 * clearly-labelled [overrides] table. Because the phoneme feature-weighted distance
 * (Phase 2.2) tolerates the residual difference, removing any single override does
 * **not** collapse a whole-word match — the rules (and the distance) still align it.
 */
object PhoneticMap {

    /** Spelling variants of the praṇava `ॐ`, resolved before any vowel folding. */
    private val omVariants = setOf("om", "aum", "oom", "aom", "oum", "ohm")

    /**
     * Minimal residual overrides for irregular STT outputs the rules cannot derive
     * (epenthetic vowels, dropped visarga, stray terminal vowels). Kept intentionally
     * tiny; each is non-critical thanks to the tolerant phonetic distance.
     */
    private val overrides = mapOf(
        "bhuva" to "bhuvah",   // dropped visarga that visarga-echo rule can't restore
        "bharago" to "bhargo", // epenthetic 'a' between r and g
        "saviture" to "savitur" // stray terminal 'e'
    )

    /**
     * Normalizes a single transliterated word to its canonical reference form.
     *
     * @param word A single lowercased-or-not transliterated word.
     * @return The canonical form, or the rule-folded form when no override applies.
     */
    fun getCanonical(word: String): String {
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
     * @return The word after vowel/`w`/visarga folding.
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
