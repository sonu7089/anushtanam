/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.reference

import com.mantrasiddhi.domain.mantra.BundledMantraCatalog
import com.mantrasiddhi.domain.mantra.Mantra
import com.mantrasiddhi.domain.mantra.MantraWord

/**
 * Static view of the **active** mantra, kept so the presentation layer keeps compiling.
 *
 * The name is historical. The reference text is no longer written in Kotlin: it is read
 * from the bundled JSON catalog, and this object simply forwards to
 * [BundledMantraCatalog.default] — whichever mantra `mantras/catalog.json` names as the
 * default. Change that field and everything below follows, including the practice screen.
 *
 * New code should take a [Mantra] (injected with `@ActiveMantra`, or passed explicitly)
 * rather than reach for a global. This object exists only to keep the UI, which still
 * reads the reference statically, from having to change in the same pass as the backend;
 * the last step of the migration is deleting it once those call sites take a [Mantra].
 */
@Deprecated(
    "Reads the catalog's default mantra. Inject the active Mantra (@ActiveMantra) instead.",
    ReplaceWith("BundledMantraCatalog.default", "com.mantrasiddhi.domain.mantra.BundledMantraCatalog")
)
object GayatriMantra {

    /** The mantra this object is a view of: the catalog's default. */
    val mantra: Mantra get() = BundledMantraCatalog.default

    /** The canonical words of the mantra, in recitation order. */
    val words: List<MantraWord> get() = mantra.words

    /** Number of words in the reference mantra. */
    val wordCount: Int get() = mantra.wordCount

    /** Number of padas (lines) in the mantra. */
    val PADA_COUNT: Int get() = mantra.padaCount

    /** Aksharas the mantra is reckoned at; a Purashcharana is measured against this. */
    val AKSHARA_COUNT: Int get() = mantra.aksharaCount

    /** Canonical Latin transliterations, in order (used for text-based scoring). */
    val transliterations: List<String> get() = mantra.transliterations

    /** Devanagari spelling of each word, in order (used for UI display). */
    val devanagariWords: List<String> get() = mantra.devanagariWords

    /** Word-index ranges per pada (element `0` is pada 1). */
    val padas: List<IntRange> get() = mantra.padas

    /** The full mantra in Devanagari, one pada per line. */
    val devanagariText: String get() = mantra.devanagariText

    /** Returns the words belonging to the given 1-based [pada]. */
    fun wordsForPada(pada: Int): List<MantraWord> = mantra.wordsForPada(pada)

    /** Returns the [MantraWord] whose transliteration matches [transliteration], or `null`. */
    fun wordFor(transliteration: String): MantraWord? = mantra.wordFor(transliteration)
}
