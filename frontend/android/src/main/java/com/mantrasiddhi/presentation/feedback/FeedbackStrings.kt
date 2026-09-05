/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.feedback

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.evaluation.feedback.ContrastiveTip
import com.mantrasiddhi.domain.evaluation.feedback.FeedbackFeature

/**
 * Maps the domain's Android-free [ContrastiveTip] onto a localized, templated string
 * from `strings.xml`.
 *
 * The domain decides *what* to say (which syllable, which feature, the correction
 * hint); this presentation-layer mapper decides *how* it reads to the user. All
 * sentences live in `strings.xml` (per AGENTS.md: no hardcoded UI strings).
 */
object FeedbackStrings {

    /** Returns the template string resource for [feature]. */
    @StringRes
    fun templateFor(feature: FeedbackFeature): Int = when (feature) {
        FeedbackFeature.DROPPED_ASPIRATION -> R.string.feedback_dropped_aspiration
        FeedbackFeature.ADDED_ASPIRATION -> R.string.feedback_added_aspiration
        FeedbackFeature.DENTAL_FOR_RETROFLEX -> R.string.feedback_dental_for_retroflex
        FeedbackFeature.RETROFLEX_FOR_DENTAL -> R.string.feedback_retroflex_for_dental
        FeedbackFeature.SHORTENED_VOWEL -> R.string.feedback_shortened_vowel
        FeedbackFeature.LENGTHENED_VOWEL -> R.string.feedback_lengthened_vowel
        FeedbackFeature.VOWEL_QUALITY -> R.string.feedback_vowel_quality
        FeedbackFeature.MISSING_VISARGA -> R.string.feedback_missing_visarga
        FeedbackFeature.MISSING_NASAL -> R.string.feedback_missing_nasal
        FeedbackFeature.MISSING_SOUND -> R.string.feedback_missing_sound
        FeedbackFeature.PLACE_OR_MANNER -> R.string.feedback_place_or_manner
        FeedbackFeature.VOICING -> R.string.feedback_voicing
        FeedbackFeature.MISSING_WORD -> R.string.feedback_missing_word
        FeedbackFeature.GENERIC -> R.string.feedback_generic
    }

    /** Resolves [tip] to its localized sentence using a [Context] (e.g. from a ViewModel). */
    fun resolve(context: Context, tip: ContrastiveTip): String = context.getString(
        templateFor(tip.feature),
        tip.spokenFragment,
        tip.syllable,
        tip.detail,
        tip.contrast,
        tip.word
    )
}

/** Composable helper that resolves a [ContrastiveTip] to its localized sentence. */
@Composable
fun contrastiveTipText(tip: ContrastiveTip): String = stringResource(
    FeedbackStrings.templateFor(tip.feature),
    tip.spokenFragment,
    tip.syllable,
    tip.detail,
    tip.contrast,
    tip.word
)
