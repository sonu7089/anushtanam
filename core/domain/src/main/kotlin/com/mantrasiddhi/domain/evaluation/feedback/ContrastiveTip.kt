/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.feedback

/**
 * A single, syllable-level contrastive coaching tip for one reference word.
 *
 * This is the structured, Android-free hand-off the presentation layer turns into a
 * localized sentence (templates live in `strings.xml`). It deliberately carries only
 * *phonetic data* — the syllable, what the learner approximately said, the key
 * sound, and a rendered correction hint — never a finished UI sentence.
 *
 * Example (`bhargo` recited as `bargo`):
 * `word="bhargo", syllable="bhar", spokenFragment="bar", detail="bh",
 *  contrast="b-h-ar", feature=DROPPED_ASPIRATION` →
 * UI: "You said *bar* — the *bh* in *bhar* needs a breath: *b-h-ar*."
 *
 * @property word The reference word (canonical transliteration) this tip is about.
 * @property syllable The specific reference syllable that needs work (e.g. `bhar`).
 * @property spokenFragment The learner's approximation of that syllable, or a dash
 *   when nothing usable was produced / the word was skipped.
 * @property detail The key sound to focus on, rendered for display (e.g. `bh`, `t`).
 * @property contrast A short correction hint derived from the syllable (e.g. the
 *   aspirate split out as `b-h-ar`), used by the template to demonstrate the fix.
 * @property feature The detected mispronunciation category driving the template.
 */
data class ContrastiveTip(
    val word: String,
    val syllable: String,
    val spokenFragment: String,
    val detail: String,
    val contrast: String,
    val feature: FeedbackFeature
)
