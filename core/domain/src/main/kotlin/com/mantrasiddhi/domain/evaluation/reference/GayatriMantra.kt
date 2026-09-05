/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.reference

/**
 * Single source of truth for the Gayatri Mantra reference text.
 *
 * Every component that previously hardcoded the mantra word list (the evaluator,
 * the practice screen display, the demo-data seeder, the mastery/weak-word use
 * cases and the benchmark audio player) now reads from this object so the
 * reference can never diverge again.
 *
 * The mantra is taught as four [padas] (lines):
 * 1. `ॐ भूर्भुवः स्वः`
 * 2. `तत्सवितुर्वरेण्यं`
 * 3. `भर्गो देवस्य धीमहि`
 * 4. `धियो यो नः प्रचोदयात्`
 *
 * Benchmark timestamps are expressed in milliseconds and align with the bundled
 * `gayatri_mantra_benchmark` recording. Parsing of `assets/benchmark_timestamps.json`
 * remains in the data layer; these baked-in values are the canonical defaults the
 * data layer refines.
 */
object GayatriMantra {

    /** The 13 canonical words of the mantra, in recitation order. */
    val words: List<MantraWord> = listOf(
        MantraWord(0, "ॐ", "om", listOf("om"), pada = 1, benchmarkStartMs = 0L, benchmarkEndMs = 3340L),
        MantraWord(1, "भूर्", "bhur", listOf("bhur"), pada = 1, benchmarkStartMs = 3390L, benchmarkEndMs = 4280L),
        MantraWord(2, "भुवः", "bhuvah", listOf("bhu", "vah"), pada = 1, benchmarkStartMs = 4290L, benchmarkEndMs = 4720L),
        MantraWord(3, "स्वः", "svah", listOf("svah"), pada = 1, benchmarkStartMs = 4840L, benchmarkEndMs = 5690L),
        MantraWord(4, "तत्सवितुर्", "tatsavitur", listOf("tat", "sa", "vi", "tur"), pada = 2, benchmarkStartMs = 6420L, benchmarkEndMs = 7220L),
        MantraWord(5, "वरेण्यं", "varenyam", listOf("va", "re", "nyam"), pada = 2, benchmarkStartMs = 7230L, benchmarkEndMs = 9050L),
        MantraWord(6, "भर्गो", "bhargo", listOf("bhar", "go"), pada = 3, benchmarkStartMs = 9400L, benchmarkEndMs = 10080L),
        MantraWord(7, "देवस्य", "devasya", listOf("de", "va", "sya"), pada = 3, benchmarkStartMs = 10090L, benchmarkEndMs = 10900L),
        MantraWord(8, "धीमहि", "dhimahi", listOf("dhi", "ma", "hi"), pada = 3, benchmarkStartMs = 10910L, benchmarkEndMs = 11920L),
        MantraWord(9, "धियो", "dhiyo", listOf("dhi", "yo"), pada = 4, benchmarkStartMs = 12060L, benchmarkEndMs = 12830L),
        MantraWord(10, "यो", "yo", listOf("yo"), pada = 4, benchmarkStartMs = 12840L, benchmarkEndMs = 13050L),
        MantraWord(11, "नः", "nah", listOf("nah"), pada = 4, benchmarkStartMs = 13200L, benchmarkEndMs = 13400L),
        MantraWord(12, "प्रचोदयात्", "prachodayat", listOf("pra", "cho", "da", "yat"), pada = 4, benchmarkStartMs = 13570L, benchmarkEndMs = 16010L)
    )

    /** Number of words in the reference mantra. */
    val wordCount: Int = words.size

    /** Number of padas (lines) in the mantra. */
    const val PADA_COUNT: Int = 4

    /**
     * Aksharas (syllables) in the Savitri gayatri proper — the three padas
     * `tatsavitur varenyam / bhargo devasya dhimahi / dhiyo yo nah prachodayat`.
     *
     * The pranava (ॐ) and the vyahritis (भूर् भुवः स्वः) are recited with it but are
     * not part of the 24, which is why the verse is called a gayatri at all: the metre
     * is three padas of eight aksharas. A Purashcharana is reckoned against this count
     * (24 x 1,00,000 = 24,00,000 japa), so it must not silently drift with the
     * word list above.
     */
    const val AKSHARA_COUNT: Int = 24

    /** Canonical Latin transliterations, in order (used for text-based scoring). */
    val transliterations: List<String> = words.map { it.transliteration }

    /** Devanagari spelling of each word, in order (used for UI display). */
    val devanagariWords: List<String> = words.map { it.devanagari }

    /**
     * Word-index ranges per pada (1-based pada → 0-based word indices).
     * Index 0 = pada 1, index 1 = pada 2, etc.
     */
    val padas: List<IntRange> = (1..PADA_COUNT).map { pada ->
        val indices = words.filter { it.pada == pada }.map { it.index }
        indices.first()..indices.last()
    }

    /** The full mantra in Devanagari, one pada per line. */
    val devanagariText: String = (1..PADA_COUNT).joinToString("\n") { pada ->
        words.filter { it.pada == pada }.joinToString(" ") { it.devanagari }
    }

    /** Returns the words belonging to the given 1-based [pada]. */
    fun wordsForPada(pada: Int): List<MantraWord> = words.filter { it.pada == pada }

    /** Returns the [MantraWord] whose transliteration matches [transliteration], or `null`. */
    fun wordFor(transliteration: String): MantraWord? {
        val key = transliteration.trim().lowercase()
        return words.firstOrNull { it.transliteration.lowercase() == key }
    }
}
