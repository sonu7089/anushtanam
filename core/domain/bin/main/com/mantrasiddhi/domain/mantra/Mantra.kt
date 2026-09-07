/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

/**
 * One mantra the app can teach, score and count japa against.
 *
 * A `Mantra` is data, not code: every instance is read from a JSON definition in the
 * bundled catalog (`core/domain/src/main/resources/mantras`) by [MantraCatalogReader].
 * Adding a mantra to the app is therefore adding a file — no engine, scorer or use case
 * needs to change. See `docs/adding-a-mantra.md`.
 *
 * Everything downstream (the evaluator, the scorers, the practice plan, the benchmark
 * audio player, the Purashcharana) reads the mantra it was given rather than a global
 * constant, so two mantras can coexist without either one's numbers leaking into the
 * other's.
 *
 * @property id Stable machine identifier, e.g. `gayatri`. Persisted alongside recitation
 *   sessions and Purashcharana vows, so it must never change once a mantra has shipped.
 * @property name Human-readable English name, e.g. `Gayatri Mantra`. Used for logs and
 *   diagnostics; user-facing copy lives in `strings.xml`.
 * @property words The words in recitation order.
 * @property padaCount Number of padas (lines) the mantra is taught in.
 * @property aksharaCount Aksharas (syllables) the mantra is *reckoned* at. This is
 *   deliberately not derived from [words]: the Gayatri is 24 aksharas because that is
 *   the metre of the Savitri verse proper, even though the pranava and the vyahritis are
 *   recited with it. A Purashcharana is measured against this number.
 * @property benchmark The reference recitation, or `null` when the mantra ships without
 *   one. Without it the acoustic and swara layers report "not assessed" and scoring
 *   degrades cleanly to the text-only path.
 * @property textRules Mantra-specific normalization of speech-to-text output.
 */
data class Mantra(
    val id: String,
    val name: String,
    val words: List<MantraWord>,
    val padaCount: Int,
    val aksharaCount: Int,
    val benchmark: MantraBenchmark? = null,
    val textRules: MantraTextRules = MantraTextRules.NONE
) {

    /** Number of words in the mantra. */
    val wordCount: Int get() = words.size

    /** Canonical Latin transliterations, in order (the reference for text scoring). */
    val transliterations: List<String> = words.map { it.transliteration }

    /** Devanagari spelling of each word, in order (used for display). */
    val devanagariWords: List<String> = words.map { it.devanagari }

    /**
     * Word-index ranges per pada: element `0` is pada 1, element `1` is pada 2, and so on.
     * Padas are contiguous by construction (the catalog reader rejects definitions whose
     * words are not grouped by pada in recitation order).
     */
    val padas: List<IntRange> = (1..padaCount).map { pada ->
        val indices = words.filter { it.pada == pada }.map { it.index }
        require(indices.isNotEmpty()) { "Mantra '$id' declares pada $pada but has no words in it" }
        indices.first()..indices.last()
    }

    /** The full mantra in Devanagari, one pada per line. */
    val devanagariText: String = padas.joinToString("\n") { range ->
        words.slice(range).joinToString(" ") { it.devanagari }
    }

    /** Per-word durations in the benchmark recording, in order; used to segment audio. */
    val benchmarkWordDurationsMs: List<Double> = words.map { it.benchmarkDurationMs.toDouble() }

    /**
     * True when the benchmark recording exists *and* every word is timed against it.
     * The acoustic and swara layers need per-word windows, so a mantra that ships audio
     * without timings is treated as having no benchmark at all.
     */
    val hasBenchmarkTimings: Boolean =
        benchmark != null && words.isNotEmpty() && words.all { it.benchmarkDurationMs > 0L }

    private val wordByTransliteration: Map<String, MantraWord> =
        words.associateBy { it.transliteration.lowercase() }

    /** Returns the words belonging to the given 1-based [pada], empty when out of range. */
    fun wordsForPada(pada: Int): List<MantraWord> =
        padas.getOrNull(pada - 1)?.let { words.slice(it) } ?: emptyList()

    /** Returns the word whose transliteration matches [transliteration], or `null`. */
    fun wordFor(transliteration: String): MantraWord? =
        wordByTransliteration[transliteration.trim().lowercase()]

    /** Zero-based position of [transliteration] in the mantra, or `null` when unknown. */
    fun indexOf(transliteration: String): Int? = wordFor(transliteration)?.index

    /**
     * Syllable count of [transliteration], used to weight a word's importance when
     * scoring pronunciation. Unknown words weigh `1` rather than zero so a stray word
     * can never divide the weighted mean by zero.
     */
    fun syllableCountFor(transliteration: String): Int =
        wordFor(transliteration)?.syllables?.size ?: 1
}
