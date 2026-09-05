/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.solar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SolarCalculatorTest {

    private fun LocalTime.minutes(): Double = hour * 60 + minute + second / 60.0

    @Test
    fun `delhi equinox times match published almanac within tolerance`() {
        val times = SolarCalculator.computeLocal(
            LocalDate.of(2025, 3, 20), 28.6139, 77.2090, ZoneId.of("Asia/Kolkata")
        )
        // Published IST values: sunrise ~06:25, solar noon ~12:29, sunset ~18:32.
        assertEquals(385.0, times.sunrise!!.minutes(), 5.0)
        assertEquals(749.0, times.solarNoon.minutes(), 5.0)
        assertEquals(1112.0, times.sunset!!.minutes(), 5.0)
        assertEquals(PolarState.NORMAL, times.polar)
    }

    @Test
    fun `london summer solstice times match published almanac within tolerance`() {
        val times = SolarCalculator.computeLocal(
            LocalDate.of(2025, 6, 21), 51.5074, -0.1278, ZoneId.of("Europe/London")
        )
        // Published BST values: sunrise ~04:43, solar noon ~13:02, sunset ~21:22.
        assertEquals(283.0, times.sunrise!!.minutes(), 5.0)
        assertEquals(782.0, times.solarNoon.minutes(), 5.0)
        assertEquals(1282.0, times.sunset!!.minutes(), 5.0)
    }

    @Test
    fun `sunrise before noon before sunset`() {
        val times = SolarCalculator.computeLocal(
            LocalDate.of(2025, 7, 1), 19.0760, 72.8777, ZoneId.of("Asia/Kolkata")
        )
        assertTrue(times.sunrise!!.minutes() < times.solarNoon.minutes())
        assertTrue(times.solarNoon.minutes() < times.sunset!!.minutes())
    }

    @Test
    fun `day is symmetric around solar noon`() {
        val times = SolarCalculator.computeLocal(
            LocalDate.of(2025, 9, 10), 13.0827, 80.2707, ZoneId.of("Asia/Kolkata")
        )
        val morning = times.solarNoon.minutes() - times.sunrise!!.minutes()
        val evening = times.sunset!!.minutes() - times.solarNoon.minutes()
        assertEquals(morning, evening, 1.0)
    }

    @Test
    fun `equinox day length is just over twelve hours due to refraction`() {
        val times = SolarCalculator.computeLocal(
            LocalDate.of(2025, 3, 20), 28.6139, 77.2090, ZoneId.of("Asia/Kolkata")
        )
        val dayLength = times.sunset!!.minutes() - times.sunrise!!.minutes()
        assertTrue(dayLength in 720.0..732.0, "expected ~12h, was $dayLength min")
    }

    @Test
    fun `pure math core is timezone-database independent`() {
        val viaZone = SolarCalculator.computeLocal(
            LocalDate.of(2025, 3, 20), 28.6139, 77.2090, ZoneId.of("Asia/Kolkata")
        )
        // IST is a fixed +330 offset; the raw-offset core must agree.
        val viaOffset = SolarCalculator.computeMinutes(2025, 3, 20, 28.6139, 77.2090, 330)
        assertEquals(viaZone.sunrise!!.minutes(), viaOffset.sunrise!!, 0.5)
        assertEquals(viaZone.sunset!!.minutes(), viaOffset.sunset!!, 0.5)
    }

    @Test
    fun `polar night near winter solstice has no sunrise or sunset`() {
        // Tromso, Norway (CET +1) in deep winter.
        val times = SolarCalculator.computeLocal(
            LocalDate.of(2025, 12, 21), 69.6492, 18.9553, ZoneId.of("Europe/Oslo")
        )
        assertNull(times.sunrise)
        assertNull(times.sunset)
        assertNotNull(times.solarNoon)
        assertEquals(PolarState.POLAR_NIGHT, times.polar)
    }

    @Test
    fun `polar day near summer solstice has no sunrise or sunset`() {
        val times = SolarCalculator.computeLocal(
            LocalDate.of(2025, 6, 21), 69.6492, 18.9553, ZoneId.of("Europe/Oslo")
        )
        assertNull(times.sunrise)
        assertNull(times.sunset)
        assertEquals(PolarState.POLAR_DAY, times.polar)
    }

    @Test
    fun `southern hemisphere has a long day in december`() {
        // Sydney (AEDT +11) — summer in the south.
        val times = SolarCalculator.computeLocal(
            LocalDate.of(2025, 12, 21), -33.8688, 151.2093, ZoneId.of("Australia/Sydney")
        )
        val dayLength = times.sunset!!.minutes() - times.sunrise!!.minutes()
        assertTrue(dayLength > 780.0, "expected > 13h summer day, was $dayLength min")
    }
}
