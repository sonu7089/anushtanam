/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
import com.mantrasiddhi.domain.evaluation.phonetics.PhoneticDistance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [PhoneticMap]: most canonicalization is now rule-based (vowel folding,
 * `w↔v`, visarga echo, `om` variants). Genuinely irregular outputs keep a minimal
 * override — and even removing an override does not collapse a whole-word match,
 * because the tolerant phonetic distance still aligns the residual form.
 */
class PhoneticMapTest {

    @Test
    fun `should_foldLongVowels_byRule`() {
        assertEquals("bhur", PhoneticMap.getCanonical("bhoor"))
        assertEquals("savitur", PhoneticMap.getCanonical("savitoor"))
        assertEquals("dhi", PhoneticMap.getCanonical("dhee"))
        assertEquals("dhimahi", PhoneticMap.getCanonical("dheemahi"))
        assertEquals("prachodayat", PhoneticMap.getCanonical("prachodayaat"))
    }

    @Test
    fun `should_foldWtoV_byRule`() {
        assertEquals("svah", PhoneticMap.getCanonical("swah"))
    }

    @Test
    fun `should_foldVisargaEcho_byRule`() {
        // swaha -> (w→v) svaha -> (…aha→…ah) svah
        assertEquals("svah", PhoneticMap.getCanonical("swaha"))
    }

    @Test
    fun `should_resolveOmVariants_byRule`() {
        assertEquals("om", PhoneticMap.getCanonical("aum"))
        assertEquals("om", PhoneticMap.getCanonical("oom"))
        assertEquals("om", PhoneticMap.getCanonical("om"))
    }

    @Test
    fun `should_passThroughCanonicalWords_unchanged`() {
        gayatri.transliterations.forEach { word ->
            assertEquals(word, PhoneticMap.getCanonical(word), "canonical '$word' must be stable")
        }
    }

    @Test
    fun `should_notCollapseMatch_when_overrideRemoved`() {
        // The residual (rule-folded) form of each irregular variant still aligns
        // strongly to its canonical word, so the override is non-critical.
        val variantToCanonical = mapOf(
            "bharago" to "bhargo",
            "saviture" to "savitur"
        )
        variantToCanonical.forEach { (variant, canonical) ->
            val folded = PhoneticMap.applyPhoneticRules(variant)
            val similarity = PhoneticDistance.wordSimilarity(canonical, folded)
            assertTrue(similarity >= 0.7, "sim($canonical, $folded)=$similarity should stay a strong match")
        }
    }
}
