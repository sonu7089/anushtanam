/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.feedback

/**
 * The specific, actionable category of mispronunciation detected for a syllable.
 *
 * This replaces the static, generic [com.mantrasiddhi.domain.evaluation.WordFeedback]
 * messages: each value names *what* went wrong at the phoneme/feature level so the
 * presentation layer can render a contrastive, localized tip from `strings.xml`
 * (the domain layer stays Android-free and carries no UI strings).
 */
enum class FeedbackFeature {
    /** A long vowel (`ā`, `ī`, `ū`) was clipped short. */
    SHORTENED_VOWEL,

    /** A short vowel was over-held / lengthened. */
    LENGTHENED_VOWEL,

    /** A different vowel timbre was produced (height/backness shift). */
    VOWEL_QUALITY,

    /** Aspiration was dropped (`bh → b`, `dh → d`, `th → t`). */
    DROPPED_ASPIRATION,

    /** Extra aspiration was added (`b → bh`). */
    ADDED_ASPIRATION,

    /** A dental was produced where the reference is retroflex. */
    DENTAL_FOR_RETROFLEX,

    /** A retroflex was produced where the reference is dental. */
    RETROFLEX_FOR_DENTAL,

    /** The trailing visarga echo (`…ḥ` / final soft `h`) was missing. */
    MISSING_VISARGA,

    /** A nasal sound was missing. */
    MISSING_NASAL,

    /** A sound was missing (general deletion). */
    MISSING_SOUND,

    /** Place or manner of articulation shifted (a clearer/harder consonant mix-up). */
    PLACE_OR_MANNER,

    /** Voicing changed (voiced↔voiceless). */
    VOICING,

    /** The whole word was skipped. */
    MISSING_WORD,

    /** A weak syllable with no single dominant, nameable feature. */
    GENERIC
}
