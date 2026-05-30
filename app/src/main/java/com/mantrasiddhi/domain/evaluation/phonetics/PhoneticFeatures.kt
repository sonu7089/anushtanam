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

    /** Max height/backness delta still treated as "the same vowel quality" (length-only change). */
    private const val QUALITY_EPSILON = 0.2

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

    /** True if [symbol] is an aspirated consonant (e.g. `bh`, `dh`, `th`). */
    fun isAspirated(symbol: String): Boolean = consonants[symbol]?.aspirated == true

    /** True if [symbol] is a nasal consonant (`m`, `n`, `ṇ`, `ṅ`, `ñ`, anusvara `ṃ`). */
    fun isNasal(symbol: String): Boolean = consonants[symbol]?.manner == Manner.NASAL

    /** True if [symbol] is the visarga (`ḥ`). */
    fun isVisarga(symbol: String): Boolean = symbol == "ḥ"

    /** True if [symbol] is a long vowel (`ā`, `ī`, `ū`, `ai`, `au`). */
    fun isLongVowel(symbol: String): Boolean = vowels[symbol]?.long == true

    /**
     * A human-meaningful classification of how the [spoken] phoneme differs from the
     * [reference] phoneme, used to drive contrastive, syllable-level feedback.
     *
     * Unlike [substitutionCost] (a scalar for scoring) this names *what* changed
     * (lost aspiration, dental↔retroflex swap, shortened long vowel, …) so the UI can
     * phrase a targeted tip instead of a generic sentence.
     *
     * @param reference The canonical phoneme that should have been produced.
     * @param spoken The canonical phoneme the learner actually produced.
     */
    fun contrast(reference: String, spoken: String): Contrast {
        if (reference == spoken) return Contrast.IDENTICAL
        val r = consonants[reference]
        val s = consonants[spoken]
        if (r != null && s != null) return consonantContrast(r, s)
        val rv = vowels[reference]
        val sv = vowels[spoken]
        if (rv != null && sv != null) return vowelContrast(rv, sv)
        return Contrast.UNRELATED
    }

    private fun consonantContrast(r: Consonant, s: Consonant): Contrast {
        val samePlace = r.place == s.place
        val sameManner = r.manner == s.manner
        val sameVoicing = r.voiced == s.voiced
        return when {
            samePlace && sameManner && sameVoicing && r.aspirated != s.aspirated ->
                if (r.aspirated) Contrast.DROPPED_ASPIRATION else Contrast.ADDED_ASPIRATION
            sameManner && sameVoicing && r.aspirated == s.aspirated &&
                r.place == Place.RETROFLEX && s.place == Place.DENTAL -> Contrast.DENTAL_FOR_RETROFLEX
            sameManner && sameVoicing && r.aspirated == s.aspirated &&
                r.place == Place.DENTAL && s.place == Place.RETROFLEX -> Contrast.RETROFLEX_FOR_DENTAL
            samePlace && sameManner && !sameVoicing -> Contrast.VOICING
            !samePlace -> Contrast.PLACE_SHIFT
            else -> Contrast.MANNER_SHIFT
        }
    }

    private fun vowelContrast(r: Vowel, s: Vowel): Contrast {
        val sameQuality = abs(r.height - s.height) < QUALITY_EPSILON &&
            abs(r.backness - s.backness) < QUALITY_EPSILON
        return when {
            r.long != s.long && sameQuality ->
                if (r.long) Contrast.SHORTENED_VOWEL else Contrast.LENGTHENED_VOWEL
            else -> Contrast.VOWEL_QUALITY
        }
    }

    /**
     * Named articulatory contrasts between a reference phoneme and a spoken one,
     * phrased from the reference's point of view (what the learner should fix).
     */
    enum class Contrast {
        IDENTICAL,
        DROPPED_ASPIRATION,
        ADDED_ASPIRATION,
        DENTAL_FOR_RETROFLEX,
        RETROFLEX_FOR_DENTAL,
        SHORTENED_VOWEL,
        LENGTHENED_VOWEL,
        VOWEL_QUALITY,
        VOICING,
        PLACE_SHIFT,
        MANNER_SHIFT,
        UNRELATED
    }

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
