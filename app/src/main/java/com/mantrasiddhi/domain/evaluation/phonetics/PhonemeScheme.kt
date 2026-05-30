/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.phonetics

/**
 * Phonetic transcription scheme that maps the many ways a single Sanskrit phoneme
 * can be spelled (lossy STT Latin, IAST diacritics, doubled long vowels) onto a
 * single canonical phoneme symbol.
 *
 * The canonical alphabet is IAST-flavoured so that distinctions the older char
 * Levenshtein threw away are preserved as *distinct tokens*:
 * - aspiration: `bh` ≠ `b`, `dh` ≠ `d`
 * - retroflex vs dental: `ṭ` ≠ `t`, `ḍ` ≠ `d`, `ṇ` ≠ `n`
 * - vowel length: `ā` ≠ `a`, `ī` ≠ `i`, `ū` ≠ `u`
 * - visarga `ḥ` and anusvara `ṃ` are their own tokens
 *
 * Both the canonical reference transliterations (plain Latin, e.g. `bhargo`) and
 * phoneme-faithful Devanagari output (IAST, e.g. `ṭ`) parse through the same table,
 * so reference and spoken words land in a comparable token space.
 */
object PhonemeScheme {

    /**
     * Spelling → canonical phoneme symbol. Keys include lossy Latin digraphs, IAST
     * diacritics and common long-vowel doublings; the parser always prefers the
     * longest matching key so digraphs are never split.
     */
    private val spellings: Map<String, String> = buildMap {
        // Aspirated stops (digraphs must win over their bare consonant)
        put("kh", "kh"); put("gh", "gh"); put("ch", "ch"); put("chh", "ch")
        put("jh", "jh"); put("ṭh", "ṭh"); put("ḍh", "ḍh"); put("th", "th")
        put("dh", "dh"); put("ph", "ph"); put("bh", "bh")
        // Retroflex series (IAST input)
        put("ṭ", "ṭ"); put("ḍ", "ḍ"); put("ṇ", "ṇ"); put("ṣ", "ś"); put("ś", "ś")
        put("ṅ", "ṅ"); put("ñ", "ñ")
        // Sibilants / aspirate
        put("sh", "ś"); put("s", "s"); put("h", "h")
        // Visarga / anusvara
        put("ḥ", "ḥ"); put("ṃ", "ṃ")
        // Plain (dental/labial/velar/palatal) consonants
        put("k", "k"); put("g", "g"); put("c", "c"); put("j", "j")
        put("t", "t"); put("d", "d"); put("n", "n")
        put("p", "p"); put("b", "b"); put("m", "m")
        put("y", "y"); put("r", "r"); put("l", "l"); put("v", "v"); put("w", "v")
        // Long vowels (diacritic + doubled forms)
        put("ā", "ā"); put("aa", "ā")
        put("ī", "ī"); put("ii", "ī"); put("ee", "ī")
        put("ū", "ū"); put("uu", "ū"); put("oo", "ū")
        put("ṛ", "ṛ"); put("ri", "ṛ")
        put("ai", "ai"); put("au", "au")
        // Short vowels
        put("a", "a"); put("i", "i"); put("u", "u"); put("e", "e"); put("o", "o")
    }

    /** Maximum spelling-key length, used to bound the greedy longest-match lookahead. */
    private val maxKeyLength: Int = spellings.keys.maxOf { it.length }

    /** Set of canonical symbols recognised by the scheme (for validation/tests). */
    val canonicalSymbols: Set<String> = spellings.values.toSet()

    /**
     * Parses [word] into an ordered list of canonical phoneme symbols.
     *
     * Unrecognised characters (e.g. STT noise like `x`, `z`) are emitted verbatim
     * as single-character tokens so they still cost something in phonetic distance
     * rather than silently disappearing.
     *
     * @param word A single transliterated word (lossy Latin or IAST).
     * @return Ordered canonical phoneme symbols; empty for a blank word.
     */
    fun parse(word: String): List<String> {
        val text = word.trim().lowercase()
        if (text.isEmpty()) return emptyList()
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val matched = matchLongestAt(text, i)
            if (matched != null) {
                tokens += spellings.getValue(matched)
                i += matched.length
            } else {
                tokens += text[i].toString()
                i++
            }
        }
        return tokens
    }

    /** Returns the longest spelling key that matches [text] at [start], or null. */
    private fun matchLongestAt(text: String, start: Int): String? {
        val maxLen = minOf(maxKeyLength, text.length - start)
        for (len in maxLen downTo 1) {
            val candidate = text.substring(start, start + len)
            if (spellings.containsKey(candidate)) return candidate
        }
        return null
    }
}
