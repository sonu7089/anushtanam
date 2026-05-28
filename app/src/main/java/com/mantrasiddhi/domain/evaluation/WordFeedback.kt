/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Feedback categories for individual spoken words.
 */
enum class WordFeedback(val message: String) {
    /**
     * Spoken word score is low, indicating poor pronunciation.
     */
    PRONUNCIATION("Focus on clearer pronunciation of this word"),

    /**
     * Reference word was completely skipped.
     */
    MISSING("This word was skipped — try to include it"),

    /**
     * Extra word was inserted by the user.
     */
    EXTRA("This word is not part of the mantra"),

    /**
     * Word is close but not fully correct.
     */
    PARTIAL("Close! Try emphasizing the ending syllable"),

    /**
     * Word is correct but out of order.
     */
    SWAPPED("Words may be in wrong order — check sequence")
}
