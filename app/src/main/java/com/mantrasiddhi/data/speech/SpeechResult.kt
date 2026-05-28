/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
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
     */
    data class Final(val text: String, val confidence: Float) : SpeechResult

    /**
     * Emitted when an error occurs during speech recognition.
     */
    data class Error(val code: Int, val message: String) : SpeechResult
}
