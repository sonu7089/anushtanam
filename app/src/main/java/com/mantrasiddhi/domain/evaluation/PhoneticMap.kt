/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation

/**
 * Map of common phonetically equivalent variations or STT misrecognitions
 * of Gayatri Mantra words to their canonical transliterated forms.
 */
object PhoneticMap {
    private val map = mapOf(
        "bhoor" to "bhur",
        "bhuva" to "bhuvah",
        "swaha" to "svah",
        "swah" to "svah",
        "savitoor" to "savitur",
        "saviture" to "savitur",
        "bharago" to "bhargo",
        "dhee" to "dhi",
        "dheemahi" to "dhimahi",
        "prachodayaat" to "prachodayat",
        "aum" to "om",
        "oom" to "om"
    )

    /**
     * Normalizes a single transliterated word using the phonetic equivalence map.
     */
    fun getCanonical(word: String): String {
        return map[word.lowercase().trim()] ?: word
    }
}
