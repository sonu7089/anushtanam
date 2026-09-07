/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.phonetics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [PhonemeTokenizer]/[PhonemeScheme]: reference words must tokenize into
 * correct phoneme sequences and the distinctions the old char distance dropped
 * (aspiration, retroflex/dental, vowel length) must survive as distinct tokens.
 */
class PhonemeTokenizerTest {

    @Test
    fun `should_tokenizeIntoPhonemeSequence_when_referenceWord`() {
        assertEquals(listOf("dh", "i", "m", "a", "h", "i"), PhonemeTokenizer.tokenize("dhimahi"))
        assertEquals(listOf("bh", "a", "r", "g", "o"), PhonemeTokenizer.tokenize("bhargo"))
    }

    @Test
    fun `should_keepDigraphsTogether_when_aspiratedConsonant`() {
        // "bh" is one token, not [b, h]; "ch"/"dh" likewise.
        assertEquals(listOf("bh", "u", "r"), PhonemeTokenizer.tokenize("bhur"))
        assertEquals(listOf("ch", "o"), PhonemeTokenizer.tokenize("cho"))
    }

    @Test
    fun `should_distinguishAspiratedFromUnaspirated`() {
        assertNotEquals(PhonemeTokenizer.tokenize("bh"), PhonemeTokenizer.tokenize("b"))
        assertEquals(listOf("bh"), PhonemeTokenizer.tokenize("bh"))
        assertEquals(listOf("b"), PhonemeTokenizer.tokenize("b"))
    }

    @Test
    fun `should_distinguishRetroflexFromDental`() {
        assertEquals(listOf("ṭ"), PhonemeTokenizer.tokenize("ṭ"))
        assertEquals(listOf("t"), PhonemeTokenizer.tokenize("t"))
        assertNotEquals(PhonemeTokenizer.tokenize("ṭ"), PhonemeTokenizer.tokenize("t"))
    }

    @Test
    fun `should_distinguishLongFromShortVowel`() {
        assertEquals(listOf("ī"), PhonemeTokenizer.tokenize("ī"))
        assertEquals(listOf("i"), PhonemeTokenizer.tokenize("i"))
        assertNotEquals(PhonemeTokenizer.tokenize("ā"), PhonemeTokenizer.tokenize("a"))
    }

    @Test
    fun `should_returnEmpty_when_blank`() {
        assertTrue(PhonemeTokenizer.tokenize("").isEmpty())
        assertTrue(PhonemeTokenizer.tokenize("   ").isEmpty())
    }

    @Test
    fun `should_groupBySyllables_when_syllablesProvided`() {
        val groups = PhonemeTokenizer.tokenizeSyllables("dhimahi", listOf("dhi", "ma", "hi"))
        assertEquals(3, groups.size)
        assertEquals(listOf("dh", "i"), groups[0])
        assertEquals(listOf("m", "a"), groups[1])
        assertEquals(listOf("h", "i"), groups[2])
    }
}
