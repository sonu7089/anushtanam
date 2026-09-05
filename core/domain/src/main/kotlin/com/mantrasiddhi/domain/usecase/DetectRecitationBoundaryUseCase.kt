/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import javax.inject.Inject

/**
 * UseCase to detect when a recitation boundary has been reached during continuous listening.
 * A boundary is detected if the transcription contains the final word of the mantra
 * ("prachodayat" / "प्रचोदयात्") or if a silence gap threshold is exceeded.
 *
 * IMPORTANT: the on-device hi-IN recognizer returns Devanagari text, so the closing
 * word must be matched in BOTH scripts. (An earlier version normalized to ASCII only,
 * which silently disabled the closing-word signal for Hindi output.)
 */
class DetectRecitationBoundaryUseCase @Inject constructor() {

    /**
     * Checks if a recitation boundary is reached.
     *
     * @param transcription The current spoken text segment.
     * @param silenceDurationMs The duration of silence detected in milliseconds.
     * @return True if a boundary is reached and the segment should be evaluated, false otherwise.
     */
    operator fun invoke(transcription: String, silenceDurationMs: Long): Boolean {
        // Boundary signal 1: the mantra's closing word is present (either script).
        if (CLOSING_WORD_REGEX.containsMatchIn(transcription)) {
            return true
        }

        // Boundary signal 2: silence gap exceeded and we have some speech.
        if (silenceDurationMs >= SILENCE_BOUNDARY_MS && transcription.isNotBlank()) {
            return true
        }

        return false
    }

    companion object {
        /** Silence gap that finalises an in-progress (possibly incomplete) recitation. */
        const val SILENCE_BOUNDARY_MS = 2000L

        /**
         * The mantra's closing word in Devanagari (with or without the trailing halant,
         * recognizers emit both) and in Latin transliteration. Used both as a boundary
         * signal and to cut per-mantra segments out of a continuous transcript.
         */
        val CLOSING_WORD_REGEX = Regex("प्रचोदयात्?|prachodayat", RegexOption.IGNORE_CASE)
    }
}
