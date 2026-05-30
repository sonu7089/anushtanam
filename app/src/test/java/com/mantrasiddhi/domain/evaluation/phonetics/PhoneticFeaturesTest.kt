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

/**
 * Tests for [PhoneticFeatures]: near-neighbour errors (aspiration, retroflex/dental,
 * vowel length) must cost less than swapping in an unrelated phoneme.
 */
class PhoneticFeaturesTest {

    @Test
    fun `should_costZero_when_identical`() {
        assertEquals(0.0, PhoneticFeatures.substitutionCost("bh", "bh"), 1e-9)
    }

    @Test
    fun `should_costLess_forAspirationLoss_thanWrongConsonant`() {
        val aspiration = PhoneticFeatures.substitutionCost("bh", "b")
        val wrong = PhoneticFeatures.substitutionCost("bh", "k")
        assertTrue(aspiration < wrong, "bh→b ($aspiration) should cost less than bh→k ($wrong)")
        assertTrue(aspiration < 0.3, "aspiration loss should be a small cost")
    }

    @Test
    fun `should_costLittle_forRetroflexDentalConfusion`() {
        val retroflexDental = PhoneticFeatures.substitutionCost("ṭ", "t")
        val unrelated = PhoneticFeatures.substitutionCost("ṭ", "s")
        assertTrue(retroflexDental < unrelated)
        assertTrue(retroflexDental < 0.3, "retroflex↔dental should be a near-neighbour cost")
    }

    @Test
    fun `should_costLittle_forVowelLengthOnly`() {
        val lengthOnly = PhoneticFeatures.substitutionCost("a", "ā")
        val differentVowel = PhoneticFeatures.substitutionCost("a", "i")
        assertTrue(lengthOnly < differentVowel)
    }

    @Test
    fun `should_costMaximum_when_vowelVersusConsonant`() {
        assertEquals(1.0, PhoneticFeatures.substitutionCost("a", "k"), 1e-9)
    }

    @Test
    fun `should_identifyVowels`() {
        assertTrue(PhoneticFeatures.isVowel("a"))
        assertTrue(PhoneticFeatures.isVowel("ī"))
        assertTrue(!PhoneticFeatures.isVowel("bh"))
    }
}
