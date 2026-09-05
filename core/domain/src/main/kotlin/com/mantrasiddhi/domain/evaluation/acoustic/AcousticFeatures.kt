/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

/**
 * A sequence of acoustic feature frames (MFCC vectors) plus the per-frame energy
 * envelope used for voice-activity segmentation.
 *
 * These are plain float arrays produced by the data-layer feature extractor
 * (`data/signal/MfccExtractor`) and consumed by the pure-Kotlin acoustic scorer —
 * keeping all Android/DSP code in the data layer and the comparison maths in the
 * domain layer.
 *
 * @property frames One MFCC coefficient vector per analysis frame, in time order.
 *   Every vector has the same dimensionality.
 * @property energies Per-frame short-time energy (same length as [frames]), used by
 *   [WordSegmenter] and [AudioQualityGate] for VAD / SNR.
 * @property frameDurationMs Time advanced by the analysis hop between consecutive
 *   frames, in milliseconds (used to convert frame counts into durations/pacing).
 */
data class AcousticFeatures(
    val frames: List<FloatArray>,
    val energies: FloatArray,
    val frameDurationMs: Double
) {
    /** Number of analysis frames. */
    val frameCount: Int get() = frames.size

    /** Whether there are no frames at all. */
    val isEmpty: Boolean get() = frames.isEmpty()

    /** Total duration spanned by the frames, in milliseconds. */
    val durationMs: Double get() = frameCount * frameDurationMs

    /**
     * Returns a contiguous slice of these features over the half-open frame range
     * `[startFrame, endFrameExclusive)`, clamped to valid bounds.
     */
    fun slice(startFrame: Int, endFrameExclusive: Int): AcousticFeatures {
        if (frames.isEmpty()) return this
        val start = startFrame.coerceIn(0, frames.size)
        val end = endFrameExclusive.coerceIn(start, frames.size)
        return AcousticFeatures(
            frames = frames.subList(start, end).toList(),
            energies = energies.copyOfRange(start.coerceAtMost(energies.size), end.coerceAtMost(energies.size)),
            frameDurationMs = frameDurationMs
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AcousticFeatures) return false
        if (frameDurationMs != other.frameDurationMs) return false
        if (frames.size != other.frames.size) return false
        if (!energies.contentEquals(other.energies)) return false
        return frames.indices.all { frames[it].contentEquals(other.frames[it]) }
    }

    override fun hashCode(): Int {
        var result = frames.fold(1) { acc, frame -> 31 * acc + frame.contentHashCode() }
        result = 31 * result + energies.contentHashCode()
        result = 31 * result + frameDurationMs.hashCode()
        return result
    }

    companion object {
        /** An empty feature sequence (no audio / extraction failed). */
        val EMPTY = AcousticFeatures(emptyList(), FloatArray(0), 0.0)
    }
}
