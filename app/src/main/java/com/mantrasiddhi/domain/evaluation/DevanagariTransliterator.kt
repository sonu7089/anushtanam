/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Transliterator that converts Devanagari script to Latin transliteration.
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

    private val directReplacements = mapOf(
        "ॐ" to "om",
        "ओम" to "om",
        "भूर्भुवः" to "bhur bhuvah",
        "भूर्" to "bhur",
        "भुवः" to "bhuvah",
        "स्वः" to "svah",
        "स्वहा" to "svah",
        "तत्सवितुर्वरेण्यं" to "tat savitur varenyam",
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
        "प्रचोदयात्" to "prachodayat"
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
}
