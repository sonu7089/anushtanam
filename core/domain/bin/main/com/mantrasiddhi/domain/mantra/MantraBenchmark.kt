/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

/**
 * The reference recitation a learner is compared against.
 *
 * The domain names the audio; it never opens it. Resolving [audioResourceName] to a
 * bundled resource and [timestampsAsset] to a file is the data layer's job, which keeps
 * this module free of Android.
 *
 * @property audioResourceName Name of the bundled raw audio resource, without extension
 *   (e.g. `gayatri_mantra_benchmark` for `core/resources/src/main/res/raw/…mp3`).
 * @property timestampsAsset Optional asset file holding refined per-word timings that
 *   override the ones baked into the mantra definition; `null` to use the definition's
 *   values alone. Missing or malformed files fall back to the definition silently, so a
 *   bad timing file can never stop a recitation being scored.
 * @property timestampsUnit The unit the values in [timestampsAsset] are expressed in.
 */
data class MantraBenchmark(
    val audioResourceName: String,
    val timestampsAsset: String? = null,
    val timestampsUnit: TimestampUnit = TimestampUnit.MILLISECONDS
) {
    /** Units a benchmark timing file may use. */
    enum class TimestampUnit(
        /** JSON spelling of this unit. */
        val jsonName: String,
        /** Multiplier that converts a value in this unit to milliseconds. */
        val millisPerUnit: Long
    ) {
        MILLISECONDS("milliseconds", 1L),
        CENTISECONDS("centiseconds", 10L);

        companion object {
            /** Resolves a JSON unit name, or `null` when it names no known unit. */
            fun fromJson(name: String): TimestampUnit? =
                entries.firstOrNull { it.jsonName.equals(name, ignoreCase = true) }

            /** Every unit name a definition file may use, for error messages. */
            val jsonNames: List<String> get() = entries.map { it.jsonName }
        }
    }
}
