/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Isolated unit tests for [LevenshteinAligner] (char distance, word similarity,
 * and word-level alignment) — previously only covered transitively.
 */
class LevenshteinAlignerTest {

    // ---- charLevenshtein ----

    @Test
    fun `should_returnZero_when_stringsAreIdentical`() {
        assertEquals(0, LevenshteinAligner.charLevenshtein("prachodayat", "prachodayat"))
        assertEquals(0, LevenshteinAligner.charLevenshtein("", ""))
    }

    @Test
    fun `should_returnLength_when_oneStringIsEmpty`() {
        assertEquals(4, LevenshteinAligner.charLevenshtein("", "bhur"))
        assertEquals(4, LevenshteinAligner.charLevenshtein("bhur", ""))
    }

    @Test
    fun `should_countSubstitution_when_singleCharDiffers`() {
        assertEquals(1, LevenshteinAligner.charLevenshtein("bhur", "bhar"))
    }

    @Test
    fun `should_countInsertionAndDeletion_when_lengthsDiffer`() {
        assertEquals(1, LevenshteinAligner.charLevenshtein("om", "aom")) // insertion
        assertEquals(1, LevenshteinAligner.charLevenshtein("svaha", "svah")) // deletion
        assertEquals(3, LevenshteinAligner.charLevenshtein("kitten", "sitting"))
    }

    @Test
    fun `should_beSymmetric_regardlessOfArgumentOrder`() {
        assertEquals(
            LevenshteinAligner.charLevenshtein("varenyam", "varenya"),
            LevenshteinAligner.charLevenshtein("varenya", "varenyam")
        )
    }

    // ---- wordSimilarity ----

    @Test
    fun `should_returnOne_when_wordsIdentical`() {
        assertEquals(1.0, LevenshteinAligner.wordSimilarity("tatsavitur", "tatsavitur"), 1e-9)
    }

    @Test
    fun `should_returnOne_when_bothEmpty`() {
        assertEquals(1.0, LevenshteinAligner.wordSimilarity("", ""), 1e-9)
    }

    @Test
    fun `should_returnZero_when_oneEmpty`() {
        assertEquals(0.0, LevenshteinAligner.wordSimilarity("", "om"), 1e-9)
    }

    @Test
    fun `should_exhibitLengthBias_when_shortWordHasOneError`() {
        // Documents the known length bias: a single wrong char costs far more on
        // a short word than on a long one.
        val shortSim = LevenshteinAligner.wordSimilarity("yo", "ya") // 1/2 -> 0.5
        val longSim = LevenshteinAligner.wordSimilarity("prachodayat", "prachodayot") // 1/11
        assertEquals(0.5, shortSim, 1e-9)
        assertTrue(longSim > 0.9)
        assertTrue(longSim > shortSim)
    }

    @Test
    fun `should_scoreBetweenZeroAndOne_when_partialMatch`() {
        val sim = LevenshteinAligner.wordSimilarity("dhimahi", "dhimah")
        assertTrue(sim in 0.0..1.0)
        assertTrue(sim > 0.8)
    }

    // ---- align ----

    @Test
    fun `should_alignOneToOne_when_sequencesIdentical`() {
        val ref = listOf("om", "bhur", "bhuvah")
        val spoken = listOf("om", "bhur", "bhuvah")
        val steps = LevenshteinAligner.align(ref, spoken)

        val matched = steps.filter { it.refIndex != null && it.spokenIndex != null }
        assertEquals(3, matched.size)
        matched.forEach { assertEquals(it.refIndex, it.spokenIndex) }
    }

    @Test
    fun `should_emitDeletion_when_referenceWordMissing`() {
        val ref = listOf("om", "bhur", "bhuvah")
        val spoken = listOf("om", "bhuvah") // dropped "bhur"
        val steps = LevenshteinAligner.align(ref, spoken)

        val deletions = steps.filter { it.refIndex != null && it.spokenIndex == null }
        assertEquals(1, deletions.size)
        assertEquals(1, deletions.first().refIndex) // "bhur"
    }

    @Test
    fun `should_emitInsertion_when_extraSpokenWord`() {
        val ref = listOf("om", "bhur")
        val spoken = listOf("om", "extra", "bhur")
        val steps = LevenshteinAligner.align(ref, spoken)

        val insertions = steps.filter { it.refIndex == null && it.spokenIndex != null }
        assertEquals(1, insertions.size)
        assertEquals("extra", spoken[insertions.first().spokenIndex!!])
    }

    @Test
    fun `should_alignSubstitution_when_wordReplaced`() {
        val ref = listOf("om", "bhur", "bhuvah")
        val spoken = listOf("om", "xyz", "bhuvah")
        val steps = LevenshteinAligner.align(ref, spoken)

        val matched = steps.filter { it.refIndex != null && it.spokenIndex != null }
        assertEquals(3, matched.size) // substitution keeps a 1:1 pairing
    }

    @Test
    fun `should_handleEmptySpoken_when_nothingRecited`() {
        val ref = listOf("om", "bhur", "bhuvah")
        val steps = LevenshteinAligner.align(ref, emptyList())

        assertEquals(3, steps.size)
        assertTrue(steps.all { it.refIndex != null && it.spokenIndex == null })
    }

    @Test
    fun `should_handleEmptyReference_when_referenceMissing`() {
        val spoken = listOf("a", "b")
        val steps = LevenshteinAligner.align(emptyList(), spoken)

        assertEquals(2, steps.size)
        assertTrue(steps.all { it.refIndex == null && it.spokenIndex != null })
    }

    @Test
    fun `should_returnEmpty_when_bothEmpty`() {
        assertTrue(LevenshteinAligner.align(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `should_preserveOrder_when_backtracking`() {
        val ref = listOf("om", "bhur", "bhuvah", "svah")
        val spoken = listOf("om", "bhur", "bhuvah", "svah")
        val steps = LevenshteinAligner.align(ref, spoken)

        val refIndices = steps.mapNotNull { it.refIndex }
        assertEquals(listOf(0, 1, 2, 3), refIndices)
    }
}
