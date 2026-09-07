/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.mantra.MantraTextRules
import com.mantrasiddhi.domain.mantra.TestMantras.gayatri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Normalization has two halves and they are tested separately on purpose: what is true of
 * Sanskrit in Latin letters must hold for **every** mantra with no rules supplied, and only
 * the residue should need a mantra's own definition.
 */
class TextNormalizerTest {

    @Test
    fun `should_normalizeScriptAndPhonetics_when_noMantraRulesSupplied`() {
        val cases = listOf(
            // Casing and spacing
            "OM" to "om",
            "  om  " to "om",
            "bhur bhuvah" to "bhur bhuvah",
            "BHUR   BHUVAH" to "bhur bhuvah",
            "svah" to "svah",

            // Punctuation and special characters
            "om," to "om",
            "bhur.bhuvah" to "bhur bhuvah",
            "svah!" to "svah",
            "varenyam?" to "varenyam",

            // General phonetic folds: long vowels, w/v, the visarga echo, pranava variants
            "bhoor" to "bhur",
            "swaha" to "svah",
            "swah" to "svah",
            "savitoor" to "savitur",
            "dhee" to "dhi",
            "dheemahi" to "dhimahi",
            "prachodayaat" to "prachodayat",
            "aum" to "om",

            // Devanagari transliteration
            "ॐ" to "om",
            "ओम" to "om",
            "भूर्भुवः" to "bhur bhuvah",
            "भूर्" to "bhur",
            "भुवः" to "bhuvah",
            "स्वः" to "svah",
            "तत्" to "tat",
            "सवितुः" to "savitur",
            "वरेण्यं" to "varenyam",
            "भर्गो देवस्य धीमहि" to "bhargo devasya dhimahi",
            "धियो यो नः प्रचोदयात्" to "dhiyo yo nah prachodayat",
            "स्वहा" to "svah",
            "देवस्य" to "devasya",
            "धीमहि" to "dhimahi"
        )

        cases.forEachIndexed { index, (input, expected) ->
            assertEquals(
                expected,
                TextNormalizer.normalize(input),
                "Case ${index + 1} failed for input: '$input'"
            )
        }
    }

    @Test
    fun `should_applyMantraOwnRules_when_definitionSuppliesThem`() {
        val cases = listOf(
            // Compounds a dictation engine runs together or splits wrongly.
            "tat-savitur" to "tatsavitur",
            "tatsaviturvarenyam" to "tatsavitur varenyam",
            "yonah" to "yo nah",
            "yona" to "yo nah",

            // Irregular outputs no general rule derives.
            "bhuva" to "bhuvah",
            "bharago" to "bhargo",
            "saviture" to "savitur",

            // The whole mantra in Devanagari, compounds and all.
            "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात्" to
                "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        )

        cases.forEachIndexed { index, (input, expected) ->
            assertEquals(
                expected,
                TextNormalizer.normalize(input, gayatri.textRules),
                "Case ${index + 1} failed for input: '$input'"
            )
        }
    }

    @Test
    fun `should_leaveTextUntouched_when_rulesAreEmpty`() {
        // The mantra-specific joins must not leak into a mantra that did not ask for them.
        assertEquals("tat savitur", TextNormalizer.normalize("tat savitur", MantraTextRules.NONE))
    }

    @Test
    fun `should_returnEmpty_when_inputIsBlankOrOnlyPunctuation`() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   "))
        assertEquals("", TextNormalizer.normalize("!!! ,,, ???"))
    }
}
