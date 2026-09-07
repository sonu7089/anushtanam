/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The catalog format is the contract between a JSON file someone (or some agent) writes by
 * hand and the whole evaluation engine, so both halves are pinned here: what a valid
 * definition produces, and that every way of getting one wrong is rejected with a message
 * naming the field.
 */
class MantraCatalogReaderTest {

    private fun mantraJson(
        id: String = "test",
        padaCount: Int = 2,
        aksharaCount: Int = 4,
        benchmark: String? = null,
        textRules: String? = null,
        words: String = """
            { "devanagari": "ॐ", "transliteration": "om", "syllables": ["om"], "pada": 1 },
            { "devanagari": "नमः", "transliteration": "namah", "syllables": ["na", "mah"], "pada": 2 }
        """
    ): String = """
        {
          "id": "$id",
          "name": "Test Mantra",
          "padaCount": $padaCount,
          "aksharaCount": $aksharaCount
          ${benchmark?.let { ", \"benchmark\": $it" } ?: ""}
          ${textRules?.let { ", \"textRules\": $it" } ?: ""},
          "words": [$words]
        }
    """.trimIndent()

    private fun read(json: String): Mantra = MantraCatalogReader.readMantra(json, "test.json")

    private fun failureFor(json: String): String =
        assertThrows<MantraCatalogException> { read(json) }.message.orEmpty()

    // --- a valid definition ----------------------------------------------------

    @Test
    fun `reads a minimal definition and derives everything not stated`() {
        val mantra = read(mantraJson())

        assertEquals("test", mantra.id)
        assertEquals("Test Mantra", mantra.name)
        assertEquals(listOf("om", "namah"), mantra.transliterations)
        assertEquals(listOf("ॐ", "नमः"), mantra.devanagariWords)
        // Word indices come from array order, never from the file.
        assertEquals(listOf(0, 1), mantra.words.map { it.index })
        assertEquals(listOf(0..0, 1..1), mantra.padas)
        assertEquals("ॐ\nनमः", mantra.devanagariText)
        assertEquals(2, mantra.syllableCountFor("namah"))
        assertNull(mantra.benchmark)
        assertTrue(mantra.textRules.isEmpty)
        assertTrue(!mantra.hasBenchmarkTimings)
    }

    @Test
    fun `lowercases ids and transliterations so lookups cannot miss on case`() {
        val mantra = read(
            mantraJson(
                id = "TeSt",
                words = """
                    { "devanagari": "ॐ", "transliteration": "OM", "syllables": ["om"], "pada": 1 },
                    { "devanagari": "नमः", "transliteration": "Namah", "syllables": ["na", "mah"], "pada": 2 }
                """
            )
        )

        assertEquals("test", mantra.id)
        assertEquals(listOf("om", "namah"), mantra.transliterations)
        assertEquals(0, mantra.indexOf("Om"))
    }

    @Test
    fun `reads a benchmark with its timing unit and per-word windows`() {
        val mantra = read(
            mantraJson(
                benchmark = """
                    {
                      "audioResourceName": "test_benchmark",
                      "timestampsAsset": "test_timestamps.json",
                      "timestampsUnit": "centiseconds"
                    }
                """,
                words = """
                    {
                      "devanagari": "ॐ", "transliteration": "om", "syllables": ["om"], "pada": 1,
                      "benchmarkStartMs": 0, "benchmarkEndMs": 500
                    },
                    {
                      "devanagari": "नमः", "transliteration": "namah", "syllables": ["na", "mah"], "pada": 2,
                      "benchmarkStartMs": 500, "benchmarkEndMs": 1200
                    }
                """
            )
        )

        assertEquals("test_benchmark", mantra.benchmark?.audioResourceName)
        assertEquals("test_timestamps.json", mantra.benchmark?.timestampsAsset)
        assertEquals(MantraBenchmark.TimestampUnit.CENTISECONDS, mantra.benchmark?.timestampsUnit)
        assertEquals(listOf(500.0, 700.0), mantra.benchmarkWordDurationsMs)
        assertTrue(mantra.hasBenchmarkTimings)
    }

    @Test
    fun `reads text rules in the order they are written`() {
        val mantra = read(
            mantraJson(
                textRules = """
                    {
                      "compoundReplacements": [
                        { "from": "Om Namah", "to": "omnamah" },
                        { "from": "omnamah", "to": "om namah" }
                      ],
                      "spellingOverrides": { "Aum": "om" }
                    }
                """
            )
        )

        assertEquals(
            listOf(
                MantraTextRules.CompoundReplacement("om namah", "omnamah"),
                MantraTextRules.CompoundReplacement("omnamah", "om namah")
            ),
            mantra.textRules.compoundReplacements
        )
        assertEquals(mapOf("aum" to "om"), mantra.textRules.spellingOverrides)
    }

    @Test
    fun `ignores keys it does not know, so a definition can carry notes`() {
        val json = mantraJson().replace("\"id\":", "\"_note\": \"provenance\", \"id\":")
        assertEquals("test", read(json).id)
    }

    // --- rejections ------------------------------------------------------------

    @Test
    fun `rejects text that is not JSON at all`() {
        val message = assertThrows<MantraCatalogException> {
            MantraCatalogReader.readMantra("not json", "broken.json")
        }.message.orEmpty()
        assertTrue(message.startsWith("broken.json is not valid JSON"), message)
    }

    @Test
    fun `names the missing field`() {
        val json = mantraJson().replace("\"aksharaCount\": 4", "\"unrelated\": 4")
        assertTrue(failureFor(json).contains("required field 'aksharaCount'"), failureFor(json))
    }

    @Test
    fun `rejects a blank name`() {
        val json = mantraJson().replace("\"name\": \"Test Mantra\"", "\"name\": \"   \"")
        assertTrue(failureFor(json).contains("'name' must not be blank"), failureFor(json))
    }

    @Test
    fun `rejects a count that is not a positive number`() {
        val json = mantraJson(aksharaCount = 0)
        assertTrue(failureFor(json).contains("'aksharaCount' must be a positive whole number"))
    }

    @Test
    fun `rejects a number written as a string`() {
        val json = mantraJson().replace("\"aksharaCount\": 4", "\"aksharaCount\": \"4\"")
        assertTrue(failureFor(json).contains("must be a number, not a quoted string"))
    }

    @Test
    fun `rejects a word whose pada is outside the declared padaCount`() {
        val json = mantraJson(padaCount = 1)
        assertTrue(failureFor(json).contains("is 2 but the mantra declares padaCount 1"), failureFor(json))
    }

    @Test
    fun `rejects padas that are not contiguous in recitation order`() {
        val json = mantraJson(
            words = """
                { "devanagari": "ॐ", "transliteration": "om", "syllables": ["om"], "pada": 2 },
                { "devanagari": "नमः", "transliteration": "namah", "syllables": ["na"], "pada": 1 }
            """
        )
        assertTrue(failureFor(json).contains("must be consecutive"), failureFor(json))
    }

    @Test
    fun `rejects a pada with no words`() {
        val json = mantraJson(padaCount = 3)
        assertTrue(failureFor(json).contains("no words in pada(s) 3"), failureFor(json))
    }

    @Test
    fun `rejects duplicate transliterations, because scores are keyed by them`() {
        val json = mantraJson(
            words = """
                { "devanagari": "ॐ", "transliteration": "om", "syllables": ["om"], "pada": 1 },
                { "devanagari": "ओम्", "transliteration": "om", "syllables": ["om"], "pada": 2 }
            """
        )
        assertTrue(failureFor(json).contains("repeats the transliteration(s) om"), failureFor(json))
    }

    @Test
    fun `rejects an empty word list`() {
        val json = mantraJson(words = "")
        assertTrue(failureFor(json).contains("'words' must list at least one word"), failureFor(json))
    }

    @Test
    fun `rejects a word with no syllables`() {
        val json = mantraJson(
            words = """
                { "devanagari": "ॐ", "transliteration": "om", "syllables": [], "pada": 1 },
                { "devanagari": "नमः", "transliteration": "namah", "syllables": ["na"], "pada": 2 }
            """
        )
        assertTrue(failureFor(json).contains("must list at least one syllable"), failureFor(json))
    }

    @Test
    fun `rejects benchmark timings that run backwards`() {
        val json = mantraJson(
            words = """
                {
                  "devanagari": "ॐ", "transliteration": "om", "syllables": ["om"], "pada": 1,
                  "benchmarkStartMs": 900, "benchmarkEndMs": 100
                },
                { "devanagari": "नमः", "transliteration": "namah", "syllables": ["na"], "pada": 2 }
            """
        )
        assertTrue(failureFor(json).contains("before benchmarkStartMs"), failureFor(json))
    }

    @Test
    fun `rejects a benchmark whose words are not timed`() {
        val json = mantraJson(benchmark = """{ "audioResourceName": "test_benchmark" }""")
        assertTrue(failureFor(json).contains("no word is timed against it"), failureFor(json))
    }

    @Test
    fun `rejects a benchmark that times only some words`() {
        val json = mantraJson(
            benchmark = """{ "audioResourceName": "test_benchmark" }""",
            words = """
                {
                  "devanagari": "ॐ", "transliteration": "om", "syllables": ["om"], "pada": 1,
                  "benchmarkStartMs": 0, "benchmarkEndMs": 500
                },
                { "devanagari": "नमः", "transliteration": "namah", "syllables": ["na"], "pada": 2 }
            """
        )
        assertTrue(failureFor(json).contains("only partly timed"), failureFor(json))
    }

    @Test
    fun `rejects an unknown timestamp unit and lists the ones that exist`() {
        val json = mantraJson(
            benchmark = """{ "audioResourceName": "b", "timestampsUnit": "furlongs" }""",
            words = """
                {
                  "devanagari": "ॐ", "transliteration": "om", "syllables": ["om"], "pada": 1,
                  "benchmarkStartMs": 0, "benchmarkEndMs": 500
                },
                {
                  "devanagari": "नमः", "transliteration": "namah", "syllables": ["na"], "pada": 2,
                  "benchmarkStartMs": 500, "benchmarkEndMs": 900
                }
            """
        )
        val message = failureFor(json)
        assertTrue(message.contains("milliseconds"), message)
        assertTrue(message.contains("centiseconds"), message)
    }

    // --- the index -------------------------------------------------------------

    @Test
    fun `reads the catalog index`() {
        val index = MantraCatalogReader.readIndex(
            """{ "defaultMantraId": "gayatri", "mantras": ["gayatri.json", "shanti.json"] }""",
            "catalog.json"
        )
        assertEquals("gayatri", index.defaultMantraId)
        assertEquals(listOf("gayatri.json", "shanti.json"), index.mantraFiles)
    }

    @Test
    fun `rejects an index that lists no mantras`() {
        val message = assertThrows<MantraCatalogException> {
            MantraCatalogReader.readIndex(
                """{ "defaultMantraId": "gayatri", "mantras": [] }""",
                "catalog.json"
            )
        }.message.orEmpty()
        assertTrue(message.contains("at least one definition file"), message)
    }
}
