/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

/**
 * A captured mono PCM recitation, expressed as normalized floating-point samples
 * in `-1.0..1.0`.
 *
 * This is the domain-layer hand-off type for the acoustic pipeline: the data layer
 * captures raw PCM (see `data/audio/AudioCaptureService`) and converts it into this
 * Android-free value object so the domain stays pure Kotlin (per the architecture
 * rules in AGENTS.md).
 *
 * @property samples Mono PCM samples in `-1.0..1.0`, in capture order.
 * @property sampleRate Sample rate in Hertz (the pipeline standardises on 16 kHz).
 */
data class RecordedAudio(
    val samples: FloatArray,
    val sampleRate: Int
) {
    /** Duration of the recording in milliseconds. */
    val durationMs: Long
        get() = if (sampleRate <= 0) 0L else samples.size * MILLIS_PER_SECOND / sampleRate

    /** Whether the recording carries any samples at all. */
    val isEmpty: Boolean get() = samples.isEmpty()

    // FloatArray breaks structural equality of data classes; override so two
    // recordings with identical samples compare equal (important for tests).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecordedAudio) return false
        return sampleRate == other.sampleRate && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int = 31 * samples.contentHashCode() + sampleRate

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
