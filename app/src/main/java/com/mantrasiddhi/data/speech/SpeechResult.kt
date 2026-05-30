/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.speech

/**
 * Result states for speech recognition.
 */
sealed interface SpeechResult {
    /**
     * Emitted when a partial transcription is available.
     */
    data class Partial(val text: String) : SpeechResult

    /**
     * Emitted when the final transcription is complete.
     *
     * @property text The 1-best transcription.
     * @property confidence Recognizer confidence for [text] in `0.0..1.0`.
     * @property alternatives The full n-best list (including [text]) as
     *   `transcription to confidence` pairs, ordered best-first. Lets the evaluator
     *   recover the correct words when they sit in a lower-ranked hypothesis.
     */
    data class Final(
        val text: String,
        val confidence: Float,
        val alternatives: List<Pair<String, Float>> = emptyList()
    ) : SpeechResult

    /**
     * Emitted when an error occurs during speech recognition.
     */
    data class Error(val code: Int, val message: String) : SpeechResult
}
