/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Isolated unit tests for [DevanagariTransliterator] — previously only covered
 * transitively via [TextNormalizer]. These pin down the current behavior (vowels,
 * matras, consonants, virama, anusvara, visarga, the full mantra and the
 * hardcoded overrides) so regressions surface when later phases rewrite it.
 */
class DevanagariTransliteratorTest {

    @Test
    fun `should_returnEmpty_when_inputEmpty`() {
        assertEquals("", DevanagariTransliterator.transliterate(""))
    }

    @Test
    fun `should_transliterateIndependentVowels`() {
        val cases = mapOf(
            "अ" to "a",
            "आ" to "a",
            "इ" to "i",
            "ई" to "i",
            "उ" to "u",
            "ऊ" to "u",
            "ऋ" to "ri",
            "ए" to "e",
            "ऐ" to "ai",
            "ओ" to "o",
            "औ" to "au",
            "ॐ" to "om"
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, DevanagariTransliterator.transliterate(input), "vowel '$input'")
        }
    }

    @Test
    fun `should_transliterateBareConsonant_withoutInherentVowel`() {
        // A single, word-final consonant carries no inherent vowel in this engine.
        assertEquals("k", DevanagariTransliterator.transliterate("क"))
        assertEquals("g", DevanagariTransliterator.transliterate("ग"))
        assertEquals("m", DevanagariTransliterator.transliterate("म"))
    }

    @Test
    fun `should_applyInherentVowel_when_consonantFollowedByConsonant`() {
        // क + म + ल -> ka + ma + l (final consonant has no inherent vowel)
        assertEquals("kamal", DevanagariTransliterator.transliterate("कमल"))
    }

    @Test
    fun `should_applyVowelSign_when_matraFollowsConsonant`() {
        assertEquals("ka", DevanagariTransliterator.transliterate("का"))
        assertEquals("ki", DevanagariTransliterator.transliterate("कि"))
        assertEquals("ku", DevanagariTransliterator.transliterate("कु"))
        assertEquals("ke", DevanagariTransliterator.transliterate("के"))
        assertEquals("ko", DevanagariTransliterator.transliterate("को"))
    }

    @Test
    fun `should_suppressVowel_when_viramaFollowsConsonant`() {
        assertEquals("k", DevanagariTransliterator.transliterate("क्"))
        // virama forming a conjunct: क् + ष -> ksh
        assertEquals("ksh", DevanagariTransliterator.transliterate("क्ष"))
    }

    @Test
    fun `should_appendAm_when_anusvaraFollowsConsonant`() {
        assertEquals("kam", DevanagariTransliterator.transliterate("कं"))
    }

    @Test
    fun `should_appendAh_when_visargaFollowsConsonant`() {
        assertEquals("kah", DevanagariTransliterator.transliterate("कः"))
    }

    @Test
    fun `should_handleOmGlyph_when_insideWord`() {
        assertEquals("omk", DevanagariTransliterator.transliterate("ॐक"))
    }

    @Test
    fun `should_passThroughLatin_when_notDevanagari`() {
        assertEquals("hello", DevanagariTransliterator.transliterate("hello"))
    }

    @Test
    fun `should_transliterateEachConsonant_withInherentVowelChain`() {
        // Two-consonant words exercise the consonant table with the inherent vowel.
        val cases = mapOf(
            "कन" to "kan",
            "गन" to "gan",
            "चन" to "chan",
            "जन" to "jan",
            "तन" to "tan",
            "दन" to "dan",
            "पन" to "pan",
            "बन" to "ban",
            "यन" to "yan",
            "रन" to "ran",
            "लन" to "lan",
            "वन" to "van",
            "सन" to "san",
            "हन" to "han"
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, DevanagariTransliterator.transliterate(input), "consonant word '$input'")
        }
    }

    @Test
    fun `should_useDirectReplacements_when_knownMantraWords`() {
        val cases = mapOf(
            "ॐ" to "om",
            "ओम" to "om",
            "भूर्भुवः" to "bhur bhuvah",
            "भूर्" to "bhur",
            "भूर" to "bhur",
            "भुवः" to "bhuvah",
            "भुवा" to "bhuvah",
            "स्वः" to "svah",
            "स्वहा" to "svah",
            "स्वाहा" to "svah",
            "तत्सवितुर्वरेण्यं" to "tat savitur varenyam",
            "तत्सवितुर्वरेंयं" to "tat savitur varenyam",
            "तत्" to "tat",
            "सवितुः" to "savitur",
            "सवितुर्" to "savitur",
            "वरेण्यं" to "varenyam",
            "भर्गो" to "bhargo",
            "देवस्य" to "devasya",
            "धीमहि" to "dhimahi",
            "धियो" to "dhiyo",
            "यो" to "yo",
            "नः" to "nah",
            "योना" to "yo nah",
            "प्रचोदयात्" to "prachodayat",
            "प्रचोदयात" to "prachodayat"
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, DevanagariTransliterator.transliterate(input), "override '$input'")
        }
    }

    @Test
    fun `should_transliterateFullMantra_viaOverrides`() {
        val input = "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात्"
        val expected = "om bhur bhuvah svah tat savitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        assertEquals(expected, DevanagariTransliterator.transliterate(input))
    }

    @Test
    fun `should_trimAndSplitOnWhitespace`() {
        assertEquals("om svah", DevanagariTransliterator.transliterate("  ॐ   स्वः  "))
    }

    // ---- Phase 2: phoneme-faithful (IAST) transliteration ----

    @Test
    fun `should_distinguishRetroflexFromDental_inFaithfulOutput`() {
        // Dental त and retroflex ट both render 't' in the lossy display path, but
        // must be distinct in the faithful path.
        assertEquals("ta", DevanagariTransliterator.transliterateFaithful("तन").take(2))
        assertEquals("ṭ", DevanagariTransliterator.transliterateFaithful("ट"))
        assertEquals("t", DevanagariTransliterator.transliterateFaithful("त"))
    }

    @Test
    fun `should_preserveAspiration_inFaithfulOutput`() {
        assertEquals("bh", DevanagariTransliterator.transliterateFaithful("भ"))
        assertEquals("b", DevanagariTransliterator.transliterateFaithful("ब"))
    }

    @Test
    fun `should_preserveVowelLength_inFaithfulOutput`() {
        assertEquals("ī", DevanagariTransliterator.transliterateFaithful("ई"))
        assertEquals("i", DevanagariTransliterator.transliterateFaithful("इ"))
        assertEquals("ū", DevanagariTransliterator.transliterateFaithful("ऊ"))
    }

    @Test
    fun `should_emitVisargaAndAnusvara_inFaithfulOutput`() {
        assertEquals("kaḥ", DevanagariTransliterator.transliterateFaithful("कः"))
        assertEquals("kaṃ", DevanagariTransliterator.transliterateFaithful("कं"))
    }

    @Test
    fun `should_returnEmpty_when_faithfulInputEmpty`() {
        assertEquals("", DevanagariTransliterator.transliterateFaithful(""))
    }
}
