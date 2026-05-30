/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.feedback

import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FeedbackComposerTest {

    private fun word(transliteration: String) = GayatriMantra.wordFor(transliteration)!!

    @Test
    fun should_composeContrastiveTip_forDroppedAspiration() {
        val tip = FeedbackComposer.compose(word("bhargo"), "bargo")
        assertNotNull(tip)
        assertEquals("bhargo", tip!!.word)
        assertEquals("bhar", tip.syllable)
        assertEquals(FeedbackFeature.DROPPED_ASPIRATION, tip.feature)
        assertEquals("b-h-ar", tip.contrast)
        assertEquals("bar", tip.spokenFragment)
    }

    @Test
    fun should_returnNull_when_wordAccurate() {
        assertNull(FeedbackComposer.compose(word("om"), "om"))
    }

    @Test
    fun should_composeMissingWordTip_when_skipped() {
        val tip = FeedbackComposer.compose(word("dhimahi"), null)
        assertNotNull(tip)
        assertEquals(FeedbackFeature.MISSING_WORD, tip!!.feature)
        assertEquals("—", tip.spokenFragment)
    }
}
