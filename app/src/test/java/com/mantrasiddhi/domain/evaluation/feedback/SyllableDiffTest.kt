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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyllableDiffTest {

    private fun word(transliteration: String) = GayatriMantra.wordFor(transliteration)!!

    @Test
    fun should_reportNoMismatch_when_wordSpokenCorrectly() {
        val diff = SyllableDiff.diff(word("dhimahi"), "dhimahi")
        assertTrue(diff.mismatches.isEmpty())
        assertNull(diff.worst)
    }

    @Test
    fun should_localiseDroppedAspiration_toCorrectSyllable() {
        // "bhargo" said as "bargo" drops the aspiration on the first syllable "bhar".
        val diff = SyllableDiff.diff(word("bhargo"), "bargo")
        val worst = diff.worst
        assertNotNull(worst)
        assertEquals("bhar", worst!!.syllable)
        assertEquals(FeedbackFeature.DROPPED_ASPIRATION, worst.feature)
    }

    @Test
    fun should_detectVowelSlip_inFirstSyllable() {
        // "dhimahi" said as "dhamahi" swaps the vowel in the first syllable "dhi".
        val diff = SyllableDiff.diff(word("dhimahi"), "dhamahi")
        assertNotNull(diff.worst)
        assertEquals("dhi", diff.worst!!.syllable)
    }

    @Test
    fun should_flagMissingWord_when_spokenNull() {
        val diff = SyllableDiff.diff(word("varenyam"), null)
        assertEquals(FeedbackFeature.MISSING_WORD, diff.worst?.feature)
    }
}
