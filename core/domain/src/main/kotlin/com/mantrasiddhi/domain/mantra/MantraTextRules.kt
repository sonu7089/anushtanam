/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

/**
 * The mantra-specific half of transcript normalization.
 *
 * General Sanskrit-in-Latin folding (long/short vowels, `w`↔`v`, the visarga echo, the
 * spellings of `ॐ`) is a property of the language and stays in
 * `com.mantrasiddhi.domain.evaluation.PhoneticMap`. What belongs here is everything that
 * is true only of *this* mantra: the compounds a dictation engine runs together, and the
 * handful of irregular outputs no rule can derive.
 *
 * Both are optional. A mantra that needs neither simply omits `textRules`, and
 * normalization falls back to the general rules alone.
 *
 * @property compoundReplacements Literal substitutions applied **in order** to the
 *   normalized transcript before it is split into words. Order matters: an earlier
 *   replacement may create the text a later one matches.
 * @property spellingOverrides Whole-word fixes keyed by the *rule-folded* form of what
 *   was heard, mapping to the canonical reference word.
 */
data class MantraTextRules(
    val compoundReplacements: List<CompoundReplacement> = emptyList(),
    val spellingOverrides: Map<String, String> = emptyMap()
) {
    /** True when this mantra adds nothing to the general normalization rules. */
    val isEmpty: Boolean
        get() = compoundReplacements.isEmpty() && spellingOverrides.isEmpty()

    /** One ordered literal substitution over a normalized transcript. */
    data class CompoundReplacement(val from: String, val to: String)

    companion object {
        /** The general rules only — no mantra-specific normalization. */
        val NONE = MantraTextRules()
    }
}
