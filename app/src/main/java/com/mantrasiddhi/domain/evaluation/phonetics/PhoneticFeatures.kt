/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.phonetics

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Articulatory feature model for the canonical phonemes emitted by [PhonemeScheme].
 *
 * The point of the model is a *graded* substitution cost: a small, common slip
 * (dropping aspiration `bh→b`, retroflex↔dental `ṭ→t`, vowel length `ā→a`) must
 * cost far less than swapping in an unrelated phoneme (`k→z`). The old char
 * Levenshtein treated every mismatch as a flat `1`, which is phonetically blind.
 */
object PhoneticFeatures {

    /** Place of articulation ordered front→back so neighbours have small distance. */
    private enum class Place { LABIAL, DENTAL, RETROFLEX, PALATAL, VELAR, GLOTTAL }

    private enum class Manner { STOP, NASAL, FRICATIVE, APPROXIMANT, LIQUID }

    /** Consonant feature bundle. */
    private data class Consonant(
        val place: Place,
        val manner: Manner,
        val voiced: Boolean,
        val aspirated: Boolean
    )

    /** Vowel feature bundle on a simple height/backness grid plus length. */
    private data class Vowel(val height: Double, val backness: Double, val long: Boolean)

    // Relative weights of each feature in the consonant substitution cost.
    private const val PLACE_WEIGHT = 0.40
    private const val MANNER_WEIGHT = 0.30
    private const val VOICING_WEIGHT = 0.15
    private const val ASPIRATION_WEIGHT = 0.15

    // Vowel substitution cost split between quality (timbre) and length.
    private const val VOWEL_QUALITY_WEIGHT = 0.60
    private const val VOWEL_LENGTH_WEIGHT = 0.15

    /** Cost of substituting a vowel for a consonant (maximally dissimilar classes). */
    private const val CROSS_CLASS_COST = 1.0

    private val placeSpan = (Place.values().size - 1).toDouble()
    private val vowelQualitySpan = sqrt(2.0) // max distance on the unit height/backness grid

    private val consonants: Map<String, Consonant> = mapOf(
        "k" to Consonant(Place.VELAR, Manner.STOP, voiced = false, aspirated = false),
        "kh" to Consonant(Place.VELAR, Manner.STOP, voiced = false, aspirated = true),
        "g" to Consonant(Place.VELAR, Manner.STOP, voiced = true, aspirated = false),
        "gh" to Consonant(Place.VELAR, Manner.STOP, voiced = true, aspirated = true),
        "ṅ" to Consonant(Place.VELAR, Manner.NASAL, voiced = true, aspirated = false),
        "c" to Consonant(Place.PALATAL, Manner.STOP, voiced = false, aspirated = false),
        "ch" to Consonant(Place.PALATAL, Manner.STOP, voiced = false, aspirated = true),
        "j" to Consonant(Place.PALATAL, Manner.STOP, voiced = true, aspirated = false),
        "jh" to Consonant(Place.PALATAL, Manner.STOP, voiced = true, aspirated = true),
        "ñ" to Consonant(Place.PALATAL, Manner.NASAL, voiced = true, aspirated = false),
        "ṭ" to Consonant(Place.RETROFLEX, Manner.STOP, voiced = false, aspirated = false),
        "ṭh" to Consonant(Place.RETROFLEX, Manner.STOP, voiced = false, aspirated = true),
        "ḍ" to Consonant(Place.RETROFLEX, Manner.STOP, voiced = true, aspirated = false),
        "ḍh" to Consonant(Place.RETROFLEX, Manner.STOP, voiced = true, aspirated = true),
        "ṇ" to Consonant(Place.RETROFLEX, Manner.NASAL, voiced = true, aspirated = false),
        "t" to Consonant(Place.DENTAL, Manner.STOP, voiced = false, aspirated = false),
        "th" to Consonant(Place.DENTAL, Manner.STOP, voiced = false, aspirated = true),
        "d" to Consonant(Place.DENTAL, Manner.STOP, voiced = true, aspirated = false),
        "dh" to Consonant(Place.DENTAL, Manner.STOP, voiced = true, aspirated = true),
        "n" to Consonant(Place.DENTAL, Manner.NASAL, voiced = true, aspirated = false),
        "p" to Consonant(Place.LABIAL, Manner.STOP, voiced = false, aspirated = false),
        "ph" to Consonant(Place.LABIAL, Manner.STOP, voiced = false, aspirated = true),
        "b" to Consonant(Place.LABIAL, Manner.STOP, voiced = true, aspirated = false),
        "bh" to Consonant(Place.LABIAL, Manner.STOP, voiced = true, aspirated = true),
        "m" to Consonant(Place.LABIAL, Manner.NASAL, voiced = true, aspirated = false),
        "y" to Consonant(Place.PALATAL, Manner.APPROXIMANT, voiced = true, aspirated = false),
        "r" to Consonant(Place.RETROFLEX, Manner.LIQUID, voiced = true, aspirated = false),
        "l" to Consonant(Place.DENTAL, Manner.LIQUID, voiced = true, aspirated = false),
        "v" to Consonant(Place.LABIAL, Manner.APPROXIMANT, voiced = true, aspirated = false),
        "ś" to Consonant(Place.PALATAL, Manner.FRICATIVE, voiced = false, aspirated = false),
        "s" to Consonant(Place.DENTAL, Manner.FRICATIVE, voiced = false, aspirated = false),
        "h" to Consonant(Place.GLOTTAL, Manner.FRICATIVE, voiced = true, aspirated = false),
        "ḥ" to Consonant(Place.GLOTTAL, Manner.FRICATIVE, voiced = false, aspirated = false),
        "ṃ" to Consonant(Place.LABIAL, Manner.NASAL, voiced = true, aspirated = false)
    )

    private val vowels: Map<String, Vowel> = mapOf(
        "a" to Vowel(height = 1.0, backness = 0.5, long = false),
        "ā" to Vowel(height = 1.0, backness = 0.5, long = true),
        "i" to Vowel(height = 0.0, backness = 0.0, long = false),
        "ī" to Vowel(height = 0.0, backness = 0.0, long = true),
        "u" to Vowel(height = 0.0, backness = 1.0, long = false),
        "ū" to Vowel(height = 0.0, backness = 1.0, long = true),
        "e" to Vowel(height = 0.3, backness = 0.0, long = false),
        "o" to Vowel(height = 0.3, backness = 1.0, long = false),
        "ai" to Vowel(height = 0.2, backness = 0.3, long = true),
        "au" to Vowel(height = 0.2, backness = 0.7, long = true),
        "ṛ" to Vowel(height = 0.5, backness = 0.4, long = false)
    )

    /** True if [symbol] is a known vowel phoneme. */
    fun isVowel(symbol: String): Boolean = vowels.containsKey(symbol)

    /**
     * Feature-weighted substitution cost between two phoneme symbols, in `0.0..1.0`.
     *
     * `0.0` means identical; near-neighbours (aspiration/retroflex/length slips)
     * score low; cross-class (vowel↔consonant) or unknown phonemes score near `1.0`.
     */
    fun substitutionCost(a: String, b: String): Double {
        if (a == b) return 0.0
        val va = vowels[a]
        val vb = vowels[b]
        val ca = consonants[a]
        val cb = consonants[b]
        return when {
            va != null && vb != null -> vowelCost(va, vb)
            ca != null && cb != null -> consonantCost(ca, cb)
            else -> CROSS_CLASS_COST
        }
    }

    private fun vowelCost(a: Vowel, b: Vowel): Double {
        val quality = sqrt(square(a.height - b.height) + square(a.backness - b.backness)) / vowelQualitySpan
        val length = if (a.long == b.long) 0.0 else 1.0
        return (quality * VOWEL_QUALITY_WEIGHT + length * VOWEL_LENGTH_WEIGHT).coerceIn(0.0, 1.0)
    }

    private fun consonantCost(a: Consonant, b: Consonant): Double {
        val place = abs(a.place.ordinal - b.place.ordinal) / placeSpan
        val manner = if (a.manner == b.manner) 0.0 else 1.0
        val voicing = if (a.voiced == b.voiced) 0.0 else 1.0
        val aspiration = if (a.aspirated == b.aspirated) 0.0 else 1.0
        val cost = place * PLACE_WEIGHT +
            manner * MANNER_WEIGHT +
            voicing * VOICING_WEIGHT +
            aspiration * ASPIRATION_WEIGHT
        return cost.coerceIn(0.0, 1.0)
    }

    private fun square(x: Double): Double = x * x
}
