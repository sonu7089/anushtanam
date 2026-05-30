/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.audio

import com.mantrasiddhi.domain.evaluation.acoustic.RecordedAudio

/**
 * A growable, capacity-capped in-memory buffer of 16-bit mono PCM samples for the
 * duration of a single recitation.
 *
 * Practice mode only needs the audio long enough to score one attempt, so the
 * buffer lives in memory (no disk) and caps itself at [maxSamples] to bound memory
 * (the cap defaults to ~30 s of 16 kHz audio). Once captured it converts to the
 * domain-friendly [RecordedAudio] for the acoustic pipeline.
 *
 * @param sampleRate Sample rate of the captured PCM, in Hz.
 * @param maxSamples Hard cap on retained samples; further writes are dropped.
 */
class PcmBuffer(
    private val sampleRate: Int,
    private val maxSamples: Int = DEFAULT_MAX_SAMPLES
) {

    private val samples = ArrayList<Short>(INITIAL_CAPACITY)

    /** Number of samples currently buffered. */
    val size: Int get() = samples.size

    /** Whether anything has been captured. */
    val isEmpty: Boolean get() = samples.isEmpty()

    /** Approximate buffered duration in milliseconds. */
    val durationMs: Long
        get() = if (sampleRate <= 0) 0L else samples.size.toLong() * MILLIS_PER_SECOND / sampleRate

    /**
     * Appends the first [length] samples from [chunk] (a block read from
     * `AudioRecord`), stopping early if the capacity cap is reached.
     */
    fun append(chunk: ShortArray, length: Int) {
        val count = length.coerceAtMost(chunk.size)
        for (i in 0 until count) {
            if (samples.size >= maxSamples) return
            samples.add(chunk[i])
        }
    }

    /** Clears the buffer for reuse. */
    fun clear() = samples.clear()

    /**
     * Converts the buffered PCM to normalised [-1, 1] mono [RecordedAudio].
     */
    fun toRecordedAudio(): RecordedAudio {
        val floats = FloatArray(samples.size) { i -> samples[i] / SHORT_SCALE }
        return RecordedAudio(samples = floats, sampleRate = sampleRate)
    }

    companion object {
        /** Standard capture sample rate for the acoustic pipeline. */
        const val SAMPLE_RATE = 16_000

        private const val MILLIS_PER_SECOND = 1000L
        private const val INITIAL_CAPACITY = 16_000 * 8
        private const val DEFAULT_MAX_SAMPLES = 16_000 * 30
        private const val SHORT_SCALE = 32_768.0f
    }
}
