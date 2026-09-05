/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.reference

/**
 * Canonical description of a single word of the Gayatri Mantra.
 *
 * This is the single source of truth for everything the evaluation engine, the
 * UI and the audio player need to know about a word: its scripts, its syllable
 * breakdown (used for syllable-level feedback), the line ([pada]) it belongs to,
 * and where it sits inside the benchmark recording.
 *
 * @property index Zero-based position of the word in the mantra sequence (0..12).
 * @property devanagari The word written in Devanagari script (e.g. "तत्सवितुर्").
 * @property transliteration Canonical Latin transliteration used for text scoring (e.g. "tatsavitur").
 * @property syllables Ordered syllables of the word, used for syllable-level feedback (e.g. ["tat","sa","vi","tur"]).
 * @property pada The line of the mantra this word belongs to, 1..4.
 * @property benchmarkStartMs Start offset of the word inside the benchmark audio, in milliseconds.
 * @property benchmarkEndMs End offset of the word inside the benchmark audio, in milliseconds.
 */
data class MantraWord(
    val index: Int,
    val devanagari: String,
    val transliteration: String,
    val syllables: List<String>,
    val pada: Int,
    val benchmarkStartMs: Long,
    val benchmarkEndMs: Long
)
