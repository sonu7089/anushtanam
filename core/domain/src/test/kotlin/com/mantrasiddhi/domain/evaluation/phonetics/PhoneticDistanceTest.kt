/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.phonetics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Tests for [PhoneticDistance]: the feature-weighted word similarity must (a) rank
 * a dropped-aspiration error above a wrong-consonant error and (b) penalise short
 * and long words comparably for an equivalent error class (the old length bias).
 */
class PhoneticDistanceTest {

    @Test
    fun `should_returnOne_when_identical`() {
        assertEquals(1.0, PhoneticDistance.wordSimilarity("prachodayat", "prachodayat"), 1e-9)
    }

    @Test
    fun `should_returnOne_when_bothEmpty`() {
        assertEquals(1.0, PhoneticDistance.wordSimilarity("", ""), 1e-9)
    }

    @Test
    fun `should_returnZero_when_oneEmpty`() {
        assertEquals(0.0, PhoneticDistance.wordSimilarity("", "om"), 1e-9)
    }

    @Test
    fun `should_scoreAspirationLossHigher_thanWrongConsonant`() {
        val dropped = PhoneticDistance.wordSimilarity("bhargo", "bargo")
        val wrong = PhoneticDistance.wordSimilarity("bhargo", "kargo")
        assertTrue(dropped > wrong, "sim(bhargo,bargo)=$dropped should beat sim(bhargo,kargo)=$wrong")
        assertTrue(dropped > 0.9, "a single dropped aspiration should stay a strong match")
    }

    @Test
    fun `should_penalizeShortAndLongWordsComparably_forEquivalentError`() {
        // One vowel substitution on a short vs a long word: with the min-length
        // floor the penalties are comparable, unlike the old length-biased metric.
        val shortWordSim = PhoneticDistance.wordSimilarity("yo", "ye")
        val longWordSim = PhoneticDistance.wordSimilarity("prachodayat", "prachodayet")
        assertTrue(abs(shortWordSim - longWordSim) < 0.15, "short=$shortWordSim long=$longWordSim")
        assertTrue(shortWordSim > 0.8, "short word single-vowel slip should not crater")
    }

    @Test
    fun `should_acceptPreTokenizedInput`() {
        val ref = PhonemeTokenizer.tokenize("dhimahi")
        val spoken = PhonemeTokenizer.tokenize("dhimahi")
        assertEquals(1.0, PhoneticDistance.wordSimilarity(ref, spoken), 1e-9)
    }
}
