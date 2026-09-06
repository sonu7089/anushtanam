/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.speech

import androidx.annotation.StringRes

/**
 * Result states for speech recognition.
 */
sealed interface SpeechResult {
    /** The provider stopped capturing; a final transcript may still be on its way. */
    data object EndOfSpeech : SpeechResult

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
     *
     * @property code The raw [android.speech.SpeechRecognizer] error code, kept so callers
     *   can react to specific failures (e.g. back off on ERROR_RECOGNIZER_BUSY).
     * @property messageResId The sadhak-facing sentence for [code]. Carried as a resource id
     *   rather than text because this reaches the screen verbatim and all copy lives in
     *   `strings.xml`.
     */
    data class Error(val code: Int, @StringRes val messageResId: Int) : SpeechResult
}
