/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextNormalizerTest {

    @Test
    fun `should_normalizeTextCorrectly_when_variousInputsProvided`() {
        val testCases = listOf(
            // 1-5: Basic Latin casing and spacing
            "OM" to "om",
            "  om  " to "om",
            "bhur bhuvah" to "bhur bhuvah",
            "BHUR   BHUVAH" to "bhur bhuvah",
            "svah" to "svah",
            
            // 6-10: Punctuation and special characters
            "om," to "om",
            "bhur.bhuvah" to "bhur bhuvah",
            "svah!" to "svah",
            "tat-savitur" to "tatsavitur",
            "varenyam?" to "varenyam",
            
            // 11-15: Phonetic map corrections
            "bhoor" to "bhur",
            "bhuva" to "bhuvah",
            "swaha" to "svah",
            "swah" to "svah",
            "savitoor" to "savitur",
            
            // 16-20: Phonetic map corrections continued
            "bharago" to "bhargo",
            "dhee" to "dhi",
            "dheemahi" to "dhimahi",
            "prachodayaat" to "prachodayat",
            "aum" to "om",
            
            // 21-25: Devanagari direct word mappings
            "ॐ" to "om",
            "ओम" to "om",
            "भूर्भुवः" to "bhur bhuvah",
            "भूर्" to "bhur",
            "भुवः" to "bhuvah",
            
            // 26-30: Devanagari direct word mappings continued
            "स्वः" to "svah",
            "तत्" to "tat",
            "सवितुः" to "savitur",
            "वरेण्यं" to "varenyam",
            "भर्गो देवस्य धीमहि" to "bhargo devasya dhimahi",
            
            // 31-35: Devanagari rules fallback
            "धियो यो नः प्रचोदयात्" to "dhiyo yo nah prachodayat",
            "स्वहा" to "svah",
            "देवस्य" to "devasya",
            "धीमहि" to "dhimahi",
            "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात्" to "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat"
        )

        testCases.forEachIndexed { index, (input, expected) ->
            val result = TextNormalizer.normalize(input)
            assertEquals(expected, result, "Test case ${index + 1} failed for input: '$input'")
        }
    }

    @Test
    fun `should_returnEmpty_when_inputIsBlankOrOnlyPunctuation`() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   "))
        assertEquals("", TextNormalizer.normalize("!!! ,,, ???"))
    }
}
