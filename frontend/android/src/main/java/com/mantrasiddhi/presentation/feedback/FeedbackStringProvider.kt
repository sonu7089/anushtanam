/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.feedback

import android.content.Context
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.evaluation.ImprovementSuggestionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable bridge that lets ViewModels turn the domain's structured feedback into
 * localized strings without depending on Compose's `stringResource`.
 *
 * It keeps every user-facing sentence in `strings.xml` while leaving the contrastive
 * logic in the domain layer.
 */
@Singleton
class FeedbackStringProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Resolves the top contrastive coaching sentence for a reference word given what
     * the learner said, or `null` when the word was produced accurately/unknown.
     *
     * @param referenceWord The canonical reference word.
     * @param spokenWord What the learner said, or `null` when skipped.
     */
    fun suggestionFor(referenceWord: String, spokenWord: String?): String? {
        val tip = ImprovementSuggestionEngine.tipForWord(referenceWord, spokenWord) ?: return null
        return FeedbackStrings.resolve(context, tip)
    }

    /** The localized "couldn't hear you clearly" message. */
    fun unclearAudioMessage(): String = context.getString(R.string.feedback_unclear_audio)
}
