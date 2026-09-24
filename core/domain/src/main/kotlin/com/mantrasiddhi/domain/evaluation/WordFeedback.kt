/*
 * Anushtanam - Vedic mantra recitation app
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
    MISSING("Try to include this word on your next recitation"),

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
    SWAPPED("Try listening once more to hear the order of the words")
}
