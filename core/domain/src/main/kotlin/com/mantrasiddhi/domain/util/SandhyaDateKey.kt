/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Single source of truth for the `yyyyMMdd` local-day key used to bucket Sandhya
 * occurrences. The zero-padded, fixed-width format sorts lexicographically in the
 * same order as chronologically, which the streak logic relies on. Every writer
 * (reminder fire, mark-as-done) and reader (metrics) must use this so an anushtanam day
 * is never split or merged across timezone/DST changes.
 */
object SandhyaDateKey {

    val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun of(date: LocalDate): String = date.format(FORMATTER)

    fun of(epochMillis: Long, zone: ZoneId): String =
        of(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())

    fun parse(dateKey: String): LocalDate = LocalDate.parse(dateKey, FORMATTER)
}
