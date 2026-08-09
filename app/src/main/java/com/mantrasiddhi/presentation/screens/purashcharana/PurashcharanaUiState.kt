/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.purashcharana

import androidx.annotation.StringRes

import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.domain.model.PurashcharanaStage

/**
 * State of the Purashcharana screen.
 *
 * [Success] carries a nullable sankalpa on purpose: the screen is just as meaningful
 * before enrolling (it is where the sadhak reads what the vow asks) as after, so
 * "no Purashcharana yet" is a successful load, not an empty state.
 */
sealed interface PurashcharanaUiState {

    data object Loading : PurashcharanaUiState

    data class Success(val purashcharana: Purashcharana? = null) : PurashcharanaUiState

    /**
     * A resource id rather than a resolved string: the ViewModel has no Context and must
     * not bake English into state. The composable resolves it with `stringResource`.
     */
    data class Error(@StringRes val messageResId: Int) : PurashcharanaUiState
}

/**
 * One-shot events from the Purashcharana screen (snackbars only).
 *
 * Taking and setting aside a sankalpa are deliberate, infrequent acts; they are
 * acknowledged once and are not part of the persistent state.
 */
sealed interface PurashcharanaEvent {

    data object Enrolled : PurashcharanaEvent

    data object Withdrawn : PurashcharanaEvent

    /**
     * @param detail A throwable's own message when one exists. It is not localizable, so
     *   it is kept out of state and formatted into `purashcharana_error_format` by the
     *   composable, which falls back to `error_unknown` when there is nothing to say.
     */
    data class ShowError(val detail: String? = null) : PurashcharanaEvent
}

/**
 * Formats a japa count the way the reader thinks about lakhs: 1,00,000 and 24,00,000,
 * not 100,000 and 2,400,000.
 *
 * Written by hand rather than via `NumberFormat`, because the Indian grouping is only
 * produced for a handful of locales and the app must show it to everyone — a sadhak in
 * London reads the same "24,00,000" as one in Pune. The rule is: last three digits, then
 * groups of two.
 */
internal fun formatIndianCount(value: Long): String {
    val digits = value.coerceAtLeast(0L).toString()
    if (digits.length <= 3) return digits

    val head = digits.substring(0, digits.length - 3)
    val tail = digits.substring(digits.length - 3)

    val grouped = StringBuilder()
    var cursor = head.length
    while (cursor > 0) {
        val start = (cursor - 2).coerceAtLeast(0)
        if (grouped.isNotEmpty()) grouped.insert(0, ',')
        grouped.insert(0, head.substring(start, cursor))
        cursor = start
    }
    return "$grouped,$tail"
}

/**
 * Percent of the sankalpa completed, as text for `purashcharana_percent_format`.
 *
 * Two decimal places below 10% because a Purashcharana is 24,00,000 japa: for the first
 * year or more a whole-number percent would sit at "0%" and read as no progress at all,
 * which is both discouraging and untrue.
 */
internal fun formatPurashcharanaPercent(progress: Float): String {
    val percent = (progress.coerceIn(0f, 1f) * 100f).toDouble()
    if (percent >= 10.0) return percent.toInt().toString()

    // Hundredths via integer maths so the result never depends on the default locale
    // (some locales would render Devanagari digits through String.format).
    val hundredths = Math.round(percent * 100.0)
    val whole = hundredths / 100L
    val fraction = hundredths % 100L
    return "$whole.${fraction.toString().padStart(2, '0')}"
}

/** Display name of a milestone stage. */
@StringRes
internal fun milestoneTitleRes(stage: PurashcharanaStage): Int = when (stage) {
    PurashcharanaStage.ARAMBHA -> R.string.milestone_arambha_title
    PurashcharanaStage.DASHA_SAHASRA -> R.string.milestone_dasha_sahasra_title
    PurashcharanaStage.SAHASRA_AVRITTI -> R.string.milestone_sahasra_avritti_title
    PurashcharanaStage.EKA_LAKSHA -> R.string.milestone_eka_laksha_title
    PurashcharanaStage.DASHAMSHA -> R.string.milestone_dashamsha_title
    PurashcharanaStage.CHATURTHAMSHA -> R.string.milestone_chaturthamsha_title
    PurashcharanaStage.ARDHA -> R.string.milestone_ardha_title
    PurashcharanaStage.TRIPADA -> R.string.milestone_tripada_title
    PurashcharanaStage.ANTIMA -> R.string.milestone_antima_title
    PurashcharanaStage.PURNAHUTI -> R.string.milestone_purnahuti_title
}

/** Plain-English gloss of a milestone stage, so the Sanskrit name never stands alone. */
@StringRes
internal fun milestoneGlossRes(stage: PurashcharanaStage): Int = when (stage) {
    PurashcharanaStage.ARAMBHA -> R.string.milestone_arambha_gloss
    PurashcharanaStage.DASHA_SAHASRA -> R.string.milestone_dasha_sahasra_gloss
    PurashcharanaStage.SAHASRA_AVRITTI -> R.string.milestone_sahasra_avritti_gloss
    PurashcharanaStage.EKA_LAKSHA -> R.string.milestone_eka_laksha_gloss
    PurashcharanaStage.DASHAMSHA -> R.string.milestone_dashamsha_gloss
    PurashcharanaStage.CHATURTHAMSHA -> R.string.milestone_chaturthamsha_gloss
    PurashcharanaStage.ARDHA -> R.string.milestone_ardha_gloss
    PurashcharanaStage.TRIPADA -> R.string.milestone_tripada_gloss
    PurashcharanaStage.ANTIMA -> R.string.milestone_antima_gloss
    PurashcharanaStage.PURNAHUTI -> R.string.milestone_purnahuti_gloss
}
