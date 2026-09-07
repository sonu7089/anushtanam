/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Transliterator that converts Devanagari script to Latin.
 *
 * Two output flavours are exposed:
 * - [transliterate] – the historical **lossy display** transliteration (retroflex
 *   and dental both render `t`, long and short vowels both render `a`/`i`/`u`).
 *   Kept for UI display and for the existing matching pipeline.
 * - [transliterateFaithful] – a **phoneme-faithful** IAST transliteration that
 *   preserves the distinctions pronunciation scoring needs (retroflex `ṭ` vs
 *   dental `t`, aspiration `bh` vs `b`, long `ī/ū`, visarga `ḥ`, anusvara `ṃ`).
 *   This output is what [com.mantrasiddhi.domain.evaluation.phonetics.PhonemeScheme]
 *   consumes for phonetic tokenization.
 */
object DevanagariTransliterator {

    private val vowels = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
        'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
        'ॐ' to "om"
    )

    private val vowelSigns = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au"
    )

    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h"
    )

    // ---- Phoneme-faithful (IAST) tables: distinctions display transliteration drops ----

    private val faithfulVowels = mapOf(
        'अ' to "a", 'आ' to "ā", 'इ' to "i", 'ई' to "ī", 'उ' to "u", 'ऊ' to "ū",
        'ऋ' to "ṛ", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
        'ॐ' to "om"
    )

    private val faithfulVowelSigns = mapOf(
        'ा' to "ā", 'ि' to "i", 'ी' to "ī", 'ु' to "u", 'ू' to "ū",
        'ृ' to "ṛ", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au"
    )

    private val faithfulConsonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ṅ",
        'च' to "c", 'छ' to "ch", 'ज' to "j", 'झ' to "jh", 'ञ' to "ñ",
        'ट' to "ṭ", 'ठ' to "ṭh", 'ड' to "ḍ", 'ढ' to "ḍh", 'ण' to "ṇ",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "ś",
        'ष' to "ṣ", 'स' to "s", 'ह' to "h"
    )

    private const val ANUSVARA = 'ं'
    private const val VISARGA = 'ः'
    private const val VIRAMA = '्'
    private const val INHERENT_VOWEL = "a"

    private val directReplacements = mapOf(
        "ॐ" to "om",
        "ओम" to "om",
        "भूर्भुवः" to "bhur bhuvah",
        "भूर्" to "bhur",
        "भूर" to "bhur",
        "भुवः" to "bhuvah",
        "भुवा" to "bhuvah",
        "स्वः" to "svah",
        "स्वहा" to "svah",
        "स्वाहा" to "svah",
        "तत्सवितुर्वरेण्यं" to "tat savitur varenyam",
        "तत्सवितुर्वरेंयं" to "tat savitur varenyam",
        "तत्" to "tat",
        "सवितुः" to "savitur",
        "सवितुर्" to "savitur",
        "वरेण्यं" to "varenyam",
        "भर्गो" to "bhargo",
        "देवस्य" to "devasya",
        "धीमहि" to "dhimahi",
        "धियो" to "dhiyo",
        "यो" to "yo",
        "नः" to "nah",
        "योना" to "yo nah",
        "प्रचोदयात्" to "prachodayat",
        "प्रचोदयात" to "prachodayat"
    )

    /**
     * Converts Devanagari text to Latin script transliteration.
     */
    fun transliterate(text: String): String {
        if (text.isEmpty()) return ""
        val normalizedInput = text.trim()
        val words = normalizedInput.split(Regex("\\s+"))
        val transliteratedWords = words.map { word ->
            directReplacements[word] ?: transliterateWord(word)
        }
        return transliteratedWords.joinToString(" ")
    }

    private fun transliterateWord(word: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < word.length) {
            val char = word[i]
            when {
                char == 'ॐ' -> {
                    sb.append("om")
                    i++
                }
                vowels.containsKey(char) -> {
                    sb.append(vowels[char])
                    i++
                }
                consonants.containsKey(char) -> {
                    i = handleConsonant(word, i, sb)
                }
                else -> {
                    sb.append(char)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun handleConsonant(word: String, index: Int, sb: java.lang.StringBuilder): Int {
        val char = word[index]
        sb.append(consonants[char])
        val nextChar = word.getOrNull(index + 1)
        if (nextChar != null) {
            when {
                vowelSigns.containsKey(nextChar) -> {
                    sb.append(vowelSigns[nextChar])
                    return index + 2
                }
                nextChar == '्' -> return index + 2
                nextChar == 'ं' -> {
                    sb.append("am")
                    return index + 2
                }
                nextChar == 'ः' -> {
                    sb.append("ah")
                    return index + 2
                }
            }
        }
        if (index < word.length - 1) {
            sb.append("a")
        }
        return index + 1
    }

    /**
     * Phoneme-faithful IAST transliteration of [text].
     *
     * Unlike [transliterate], this preserves retroflex/dental, aspiration, vowel
     * length, visarga and anusvara as distinct symbols, so the result can be parsed
     * into faithful phoneme tokens. The lossy whole-word overrides are intentionally
     * **not** applied here.
     *
     * @param text Devanagari (or mixed) input.
     * @return Faithful IAST transliteration.
     */
    fun transliterateFaithful(text: String): String {
        if (text.isEmpty()) return ""
        val words = text.trim().split(Regex("\\s+"))
        return words.joinToString(" ") { transliterateFaithfulWord(it) }
    }

    private fun transliterateFaithfulWord(word: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < word.length) {
            val char = word[i]
            when {
                char == 'ॐ' -> { sb.append("om"); i++ }
                faithfulVowels.containsKey(char) -> { sb.append(faithfulVowels[char]); i++ }
                faithfulConsonants.containsKey(char) -> i = handleFaithfulConsonant(word, i, sb)
                else -> { sb.append(char); i++ }
            }
        }
        return sb.toString()
    }

    private fun handleFaithfulConsonant(word: String, index: Int, sb: StringBuilder): Int {
        sb.append(faithfulConsonants[word[index]])
        val nextChar = word.getOrNull(index + 1)
        if (nextChar != null) {
            val sign = faithfulVowelSigns[nextChar]
            when {
                sign != null -> { sb.append(sign); return index + 2 }
                nextChar == VIRAMA -> return index + 2
                nextChar == ANUSVARA -> { sb.append(INHERENT_VOWEL).append("ṃ"); return index + 2 }
                nextChar == VISARGA -> { sb.append(INHERENT_VOWEL).append("ḥ"); return index + 2 }
            }
        }
        if (index < word.length - 1) sb.append(INHERENT_VOWEL)
        return index + 1
    }
}
