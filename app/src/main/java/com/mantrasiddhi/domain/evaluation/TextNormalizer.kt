/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Normalization pipeline for speech-to-text outputs and reference mantra texts.
 */
object TextNormalizer {

    /**
     * Normalizes the input text by:
     * 1. Normalizing Unicode characters (NFC).
     * 2. Transliterating Devanagari characters to Latin script.
     * 3. Lowercasing all characters.
     * 4. Replacing punctuation and special characters with spaces.
     * 5. Collapsing multiple spaces and trimming whitespace.
     * 6. Applying phonetic equivalence mapping to map common STT errors to canonical words.
     *
     * @param text The raw transcription or reference text.
     * @return The fully normalized text ready for word-level comparison.
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        
        // 1. Normalize Unicode (NFC)
        val nfcNormalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)

        // 2. Transliterate Devanagari script to Latin script
        val transliterated = DevanagariTransliterator.transliterate(nfcNormalized)

        // 3. Lowercase
        val lowercased = transliterated.lowercase()

        // 4. Remove punctuation & special characters (replace with spaces)
        val cleanPunctuation = lowercased.replace(Regex("[.,\\/#!$%\\^&\\*;:{}=\\-_`~()?'\"।॥]"), " ")

        // 5. Normalize whitespace
        val cleanWhitespace = cleanPunctuation.replace(Regex("\\s+"), " ").trim()

        if (cleanWhitespace.isEmpty()) return ""

        // 6. Apply phonetic equivalence mapping word-by-word
        val words = cleanWhitespace.split(" ")
        val normalizedWords = words.map { word ->
            PhoneticMap.getCanonical(word)
        }

        return normalizedWords.joinToString(" ")
    }
}
