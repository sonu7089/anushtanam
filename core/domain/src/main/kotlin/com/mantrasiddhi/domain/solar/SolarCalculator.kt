/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.solar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Whether a given day at a given latitude has a normal sunrise/sunset, or is in
 * continuous daylight ([POLAR_DAY]) or continuous night ([POLAR_NIGHT]).
 */
enum class PolarState { NORMAL, POLAR_DAY, POLAR_NIGHT }

/** Sunrise / solar-noon / sunset as wall-clock times in the requested zone. */
data class SolarTimes(
    val sunrise: LocalTime?,   // null on polar day/night
    val solarNoon: LocalTime,  // always defined (zenith is defined at every latitude)
    val sunset: LocalTime?,    // null on polar day/night
    val polar: PolarState
)

/** The same three events as absolute UTC epoch milliseconds. */
data class SolarEpochs(
    val sunriseUtcMillis: Long?,
    val solarNoonUtcMillis: Long,
    val sunsetUtcMillis: Long?,
    val polar: PolarState
)

/**
 * Self-contained NOAA / "sunrise equation" solar-position calculator.
 *
 * The astronomy is pure math (no Android, no network, no data tables); only the
 * final wall-clock/epoch conversions touch `java.time`. Accuracy is well under a
 * minute at non-polar latitudes, which is ample for a devotional reminder.
 *
 * Angles are in **degrees** throughout and only converted to radians inside trig
 * calls, matching the published NOAA spreadsheet so reference values line up.
 *
 * The 90.833° zenith bakes in the standard 50' allowance (≈34' mean atmospheric
 * refraction at the horizon + 16' solar semi-diameter), the convention every
 * almanac uses for "official" sunrise/sunset.
 */
object SolarCalculator {

    private const val ZENITH_DEG = 90.833

    /** Julian Day for a (possibly fractional) Gregorian civil date. */
    fun julianDay(year: Int, month: Int, dayFrac: Double): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + dayFrac + b - 1524.5
    }

    private fun julianCentury(jd: Double): Double = (jd - 2451545.0) / 36525.0

    /** Solar declination (deg) and equation of time (minutes) at a Julian Day. */
    private data class Geometry(val declinationDeg: Double, val equationOfTimeMin: Double)

    private fun geometryAt(jd: Double): Geometry {
        val t = julianCentury(jd)
        val l0 = ((280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0 + 360.0) % 360.0
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mRad = Math.toRadians(m)
        val c = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3 * mRad) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val appLong = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(Math.toRadians(omega))
        val declination = Math.toDegrees(asin(sin(Math.toRadians(eps)) * sin(Math.toRadians(appLong))))
        val varY = tan(Math.toRadians(eps / 2.0)).pow(2)
        val l0Rad = Math.toRadians(l0)
        val equationOfTime = 4.0 * Math.toDegrees(
            varY * sin(2 * l0Rad) - 2 * e * sin(mRad) +
                4 * e * varY * sin(mRad) * cos(2 * l0Rad) -
                0.5 * varY * varY * sin(4 * l0Rad) -
                1.25 * e * e * sin(2 * mRad)
        )
        return Geometry(declination, equationOfTime)
    }

    /** Sunrise/solar-noon/sunset as minutes past local midnight, plus polar state. */
    data class Minutes(
        val sunrise: Double?,
        val noon: Double,
        val sunset: Double?,
        val polar: PolarState
    )

    /**
     * Pure-math core: timezone-database-free. [tzOffsetMin] is minutes east of UTC
     * (e.g. 330 for IST). One noon re-centering iteration gives sub-second accuracy
     * and removes the small sunrise/sunset asymmetry of a 0h-geometry estimate.
     */
    fun computeMinutes(
        year: Int,
        month: Int,
        day: Int,
        latDeg: Double,
        lonDeg: Double,
        tzOffsetMin: Int
    ): Minutes {
        // Pass 1: geometry at local midnight.
        var jd = julianDay(year, month, day.toDouble()) - tzOffsetMin / 1440.0
        var geometry = geometryAt(jd)
        var noon = 720.0 - 4.0 * lonDeg - geometry.equationOfTimeMin + tzOffsetMin
        // Pass 2: re-center geometry on the estimated local solar noon.
        jd = julianDay(year, month, day + noon / 1440.0) - tzOffsetMin / 1440.0
        geometry = geometryAt(jd)
        noon = 720.0 - 4.0 * lonDeg - geometry.equationOfTimeMin + tzOffsetMin

        val latRad = Math.toRadians(latDeg)
        val declRad = Math.toRadians(geometry.declinationDeg)
        val cosH = (cos(Math.toRadians(ZENITH_DEG)) - sin(latRad) * sin(declRad)) /
            (cos(latRad) * cos(declRad))

        // Polar classification keyed off cosH (correct at every latitude); a
        // declination-sign heuristic mislabels days near the equinoxes at extreme
        // latitudes, so we never use it.
        if (cosH > 1.0) return Minutes(null, noon, null, PolarState.POLAR_NIGHT)
        if (cosH < -1.0) return Minutes(null, noon, null, PolarState.POLAR_DAY)

        val hourAngle = Math.toDegrees(acos(cosH))
        return Minutes(noon - 4.0 * hourAngle, noon, noon + 4.0 * hourAngle, PolarState.NORMAL)
    }

    private fun toLocalTime(minutesPastMidnight: Double): LocalTime {
        val secs = ((minutesPastMidnight % 1440.0 + 1440.0) % 1440.0) * 60.0
        return LocalTime.ofSecondOfDay(secs.roundToLong().coerceIn(0, 86_399))
    }

    /** Wall-clock sunrise/noon/sunset for [date] at [latDeg]/[lonDeg] in [zone]. */
    fun computeLocal(date: LocalDate, latDeg: Double, lonDeg: Double, zone: ZoneId): SolarTimes {
        // Use local noon to pick the correct DST offset for the specific day.
        val tzOffsetMin = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 60
        val mn = computeMinutes(date.year, date.monthValue, date.dayOfMonth, latDeg, lonDeg, tzOffsetMin)
        return SolarTimes(
            sunrise = mn.sunrise?.let(::toLocalTime),
            solarNoon = toLocalTime(mn.noon),
            sunset = mn.sunset?.let(::toLocalTime),
            polar = mn.polar
        )
    }

    /** The same three events as absolute UTC epoch milliseconds. */
    fun computeEpochMillis(date: LocalDate, latDeg: Double, lonDeg: Double, zone: ZoneId): SolarEpochs {
        val times = computeLocal(date, latDeg, lonDeg, zone)
        fun millis(time: LocalTime?): Long? =
            time?.let { ZonedDateTime.of(date, it, zone).toInstant().toEpochMilli() }
        return SolarEpochs(
            sunriseUtcMillis = millis(times.sunrise),
            solarNoonUtcMillis = millis(times.solarNoon)!!,
            sunsetUtcMillis = millis(times.sunset),
            polar = times.polar
        )
    }
}
