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
 * Guards the catalog that actually ships.
 *
 * The reader's own tests cover the format; these check the shipped *data* — that every
 * bundled definition parses, that the default resolves, and that the Gayatri still says
 * exactly what it said when its text lived in Kotlin. The last part is the safety net for
 * the move to JSON: had a single word, syllable or timing changed, every learner's word
 * history would silently mean something different.
 */
class BundledMantraCatalogTest {

    private val catalog: MantraCatalog = BundledMantraCatalog

    @Test
    fun `every bundled definition loads and the default resolves`() {
        assertTrue(catalog.mantras.isNotEmpty())
        assertTrue(catalog.mantras.contains(catalog.default))
        catalog.mantras.forEach { mantra ->
            assertTrue(mantra.id.isNotBlank())
            assertTrue(mantra.words.isNotEmpty(), "${mantra.id} has no words")
            assertTrue(mantra.aksharaCount > 0, "${mantra.id} has no aksharas")
        }
    }

    @Test
    fun `mantra ids are unique across the catalog`() {
        val ids = catalog.mantras.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `find and require agree, and an unknown id fails loudly`() {
        val id = catalog.default.id
        assertEquals(catalog.default, catalog.find(id))
        assertEquals(catalog.default, catalog.require(id))
        assertNull(catalog.find("no-such-mantra"))

        val message = assertThrows<MantraCatalogException> { catalog.require("no-such-mantra") }
            .message.orEmpty()
        assertTrue(message.contains("no-such-mantra"), message)
        assertTrue(message.contains(id), message)
    }

    @Test
    fun `wordFor searches the catalog for a stored transliteration`() {
        val word = catalog.default.words.first()
        assertEquals(word, catalog.wordFor(word.transliteration))
        assertNull(catalog.wordFor("notamantraword"))
    }

    // --- the Gayatri, exactly as it was before it moved into JSON ---------------

    private val gayatri: Mantra get() = catalog.require("gayatri")

    @Test
    fun `the Gayatri still has its thirteen words in order`() {
        assertEquals(
            listOf(
                "om", "bhur", "bhuvah", "svah",
                "tatsavitur", "varenyam",
                "bhargo", "devasya", "dhimahi",
                "dhiyo", "yo", "nah", "prachodayat"
            ),
            gayatri.transliterations
        )
        assertEquals(13, gayatri.wordCount)
    }

    @Test
    fun `the Gayatri still has four padas and twenty-four aksharas`() {
        assertEquals(4, gayatri.padaCount)
        // 24 x 1,00,000 japa is what a Purashcharana is measured against. If this ever
        // changes, every sadhak's vow changes length with it.
        assertEquals(24, gayatri.aksharaCount)
        assertEquals(listOf(0..3, 4..5, 6..8, 9..12), gayatri.padas)
    }

    @Test
    fun `the Gayatri still reads as four lines of Devanagari`() {
        assertEquals(
            """
            ॐ भूर् भुवः स्वः
            तत्सवितुर् वरेण्यं
            भर्गो देवस्य धीमहि
            धियो यो नः प्रचोदयात्
            """.trimIndent(),
            gayatri.devanagariText
        )
    }

    @Test
    fun `the Gayatri still carries its syllable breakdown, which drives feedback`() {
        assertEquals(listOf("tat", "sa", "vi", "tur"), gayatri.wordFor("tatsavitur")?.syllables)
        assertEquals(listOf("pra", "cho", "da", "yat"), gayatri.wordFor("prachodayat")?.syllables)
        assertEquals(1, gayatri.syllableCountFor("yo"))
        assertEquals(4, gayatri.syllableCountFor("prachodayat"))
    }

    @Test
    fun `the Gayatri still points at its benchmark recording and its timings`() {
        val benchmark = requireNotNull(gayatri.benchmark) { "the Gayatri must ship a benchmark" }
        assertEquals("gayatri_mantra_benchmark", benchmark.audioResourceName)
        assertEquals("benchmark_timestamps.json", benchmark.timestampsAsset)
        assertEquals(MantraBenchmark.TimestampUnit.CENTISECONDS, benchmark.timestampsUnit)
        assertTrue(gayatri.hasBenchmarkTimings)

        assertEquals(0L, gayatri.words.first().benchmarkStartMs)
        assertEquals(16_010L, gayatri.words.last().benchmarkEndMs)
        // Windows run forwards and never overlap.
        gayatri.words.zipWithNext { a, b ->
            assertTrue(a.benchmarkEndMs <= b.benchmarkStartMs, "${a.transliteration} overlaps ${b.transliteration}")
        }
    }
}
