/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.audio

/**
 * Represents the timing information of a single word in the benchmark audio.
 *
 * Used for synchronizing word-by-word highlighting during playback.
 *
 * @property wordIndex The 0-based index of the word in the mantra sequence.
 * @property word The text of the word.
 * @property startMs The timestamp in milliseconds when the word begins.
 * @property endMs The timestamp in milliseconds when the word ends.
 */
data class WordTimestamp(
    val wordIndex: Int,
    val word: String,
    val startMs: Long,
    val endMs: Long
)
