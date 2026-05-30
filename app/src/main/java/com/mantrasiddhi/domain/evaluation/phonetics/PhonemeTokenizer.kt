/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.phonetics

/**
 * Converts transliterated words into ordered phoneme-token lists (and, when a
 * syllable breakdown is supplied, into per-syllable token groups) using
 * [PhonemeScheme].
 *
 * This is the single entry point the scoring code uses to move from "strings of
 * letters" into the phonetic token space where feature-weighted distances and
 * syllable-level feedback become possible.
 */
object PhonemeTokenizer {

    /**
     * Tokenizes a single [word] into canonical phoneme symbols.
     *
     * @param word A transliterated word (lossy Latin or IAST).
     * @return Ordered phoneme tokens, e.g. `dhimahi → [dh, i, m, a, h, i]`.
     */
    fun tokenize(word: String): List<String> = PhonemeScheme.parse(word)

    /**
     * Tokenizes a word into per-syllable phoneme groups.
     *
     * When [syllables] is provided (e.g. from the reference [com.mantrasiddhi
     * .domain.evaluation.reference.MantraWord]) each syllable is tokenized
     * independently so feedback can localise to a syllable. When it is null the
     * whole word is returned as a single group.
     *
     * @param word The whole word, used as a fallback when [syllables] is null.
     * @param syllables Optional ordered syllables of [word].
     * @return A list of phoneme-token groups, one per syllable.
     */
    fun tokenizeSyllables(word: String, syllables: List<String>? = null): List<List<String>> {
        if (syllables.isNullOrEmpty()) return listOf(tokenize(word))
        return syllables.map { tokenize(it) }
    }
}
