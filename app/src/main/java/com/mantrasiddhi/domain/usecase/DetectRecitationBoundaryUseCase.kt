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
 * A boundary is detected if the transcription contains the final word ("prachodayat")
 * or if a silence gap threshold is exceeded.
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
        val normalized = transcription.lowercase().replace("[^a-zA-Z\\s]".toRegex(), "").trim()
        val words = normalized.split("\\s+".toRegex())
        
        // Boundary signal 1: Last word of the mantra is present
        if (words.contains("prachodayat") || normalized.endsWith("prachodayat")) {
            return true
        }
        
        // Boundary signal 2: Silence gap is greater than 2 seconds and we have some speech
        if (silenceDurationMs >= 2000L && normalized.isNotEmpty()) {
            return true
        }
        
        return false
    }
}
