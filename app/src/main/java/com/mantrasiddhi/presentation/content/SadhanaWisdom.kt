/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.content

import androidx.annotation.StringRes

import com.mantrasiddhi.R

/**
 * One short teaching from the Sadhana Marg — the path a sadhak actually walks.
 *
 * Only resource ids are held. The copy itself is deliberately long-form and lives in
 * strings.xml, both because every user-visible string must be translatable and because
 * these teachings are edited by the app owner far more often than this file is.
 */
data class SadhanaTeaching(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int
)

/**
 * The fixed set of Sadhana Marg teachings shown on Home, one at a time.
 *
 * ## Why a rotation and not a random pick
 * A teaching chosen at random would change on every recomposition — the card would
 * flicker while the sadhak was reading it. The teaching is therefore keyed to the
 * calendar day: it is the same all day, and it moves on by itself tomorrow, which is
 * also the rhythm the content is written for (one small thing, absorbed, then the next).
 *
 * The order matches the string contract, and it is not accidental: nadi shuddhi opens
 * the sequence because it is the teaching the owner asked for by name, and shraddha
 * closes it because earnestness is what the whole sequence is arguing for.
 */
object SadhanaWisdom {

    val teachings: List<SadhanaTeaching> = listOf(
        SadhanaTeaching(R.string.wisdom_nadi_shuddhi_title, R.string.wisdom_nadi_shuddhi_body),
        SadhanaTeaching(R.string.wisdom_akshara_title, R.string.wisdom_akshara_body),
        SadhanaTeaching(R.string.wisdom_swara_title, R.string.wisdom_swara_body),
        SadhanaTeaching(R.string.wisdom_trikala_title, R.string.wisdom_trikala_body),
        SadhanaTeaching(R.string.wisdom_mala_title, R.string.wisdom_mala_body),
        SadhanaTeaching(R.string.wisdom_ajapa_title, R.string.wisdom_ajapa_body),
        SadhanaTeaching(R.string.wisdom_niyama_title, R.string.wisdom_niyama_body),
        SadhanaTeaching(R.string.wisdom_shraddha_title, R.string.wisdom_shraddha_body)
    )

    /** How many teachings there are; the "next" affordance wraps around this. */
    val count: Int = teachings.size

    /**
     * Index of the teaching for a given day of the year. Stable for the whole day, so a
     * composable can call it as often as it likes without the card changing under the
     * reader.
     */
    fun indexForDay(dayOfYear: Int): Int = dayOfYear.mod(count)

    /** The teaching for [dayOfYear] — the one Home opens on. */
    fun teachingOfTheDay(dayOfYear: Int): SadhanaTeaching = teachings[indexForDay(dayOfYear)]

    /**
     * Teaching at [index], wrapping in both directions, so the "next teaching" button can
     * simply increment a counter forever without the caller doing modulo arithmetic.
     */
    fun teachingAt(index: Int): SadhanaTeaching = teachings[index.mod(count)]
}
