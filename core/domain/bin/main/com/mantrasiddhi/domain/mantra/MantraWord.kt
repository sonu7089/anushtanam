/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

/**
 * Canonical description of a single word of a [Mantra].
 *
 * This is everything the evaluation engine, the UI and the audio player need to know
 * about a word: its two scripts, its syllable breakdown (used for syllable-level
 * feedback), the line ([pada]) it belongs to, and where it sits inside the benchmark
 * recording.
 *
 * Instances are built by [MantraCatalogReader] from the bundled catalog, never by hand:
 * [index] is assigned from the word's position in the definition file so it can never
 * disagree with the recitation order.
 *
 * @property index Zero-based position of the word in the mantra sequence.
 * @property devanagari The word written in Devanagari script (e.g. `तत्सवितुर्`).
 * @property transliteration Canonical Latin transliteration used for text scoring
 *   (e.g. `tatsavitur`). Unique within a mantra.
 * @property syllables Ordered syllables of the word, used for syllable-level feedback
 *   (e.g. `["tat", "sa", "vi", "tur"]`). Never empty.
 * @property pada The 1-based line of the mantra this word belongs to.
 * @property benchmarkStartMs Start offset of the word inside the benchmark audio, in
 *   milliseconds. `0` for both offsets when the mantra ships without a benchmark.
 * @property benchmarkEndMs End offset of the word inside the benchmark audio, in
 *   milliseconds.
 */
data class MantraWord(
    val index: Int,
    val devanagari: String,
    val transliteration: String,
    val syllables: List<String>,
    val pada: Int,
    val benchmarkStartMs: Long = 0L,
    val benchmarkEndMs: Long = 0L
) {
    /** How long this word lasts in the benchmark recording; `0` when it has no timings. */
    val benchmarkDurationMs: Long get() = (benchmarkEndMs - benchmarkStartMs).coerceAtLeast(0L)
}
