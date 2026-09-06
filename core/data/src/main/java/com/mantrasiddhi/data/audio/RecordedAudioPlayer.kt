/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.mantrasiddhi.domain.evaluation.acoustic.RecordedAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays back the learner's own captured PCM (or a single-word slice of it) through an
 * [AudioTrack], enabling A/B "hear yourself vs the reference" comparison (Phase 4.2).
 *
 * Lifecycle mirrors [BenchmarkAudioPlayer] / [AudioCaptureService]: each playback owns
 * a short-lived `AudioTrack` that is released on completion, [stop] or [release], so
 * audio focus is never leaked on screen exit. Playback is best-effort and never throws.
 */
@Singleton
class RecordedAudioPlayer @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playJob: Job? = null
    private var audioTrack: AudioTrack? = null

    /**
     * Plays the whole [audio] recording.
     *
     * @param audio The learner's captured recitation.
     */
    fun play(audio: RecordedAudio) {
        if (audio.isEmpty) return
        playRange(audio, 0, audio.samples.size - 1)
    }

    /**
     * Plays the inclusive sample [range] of [audio]; no-op when the range is empty or
     * out of bounds.
     *
     * @param audio The learner's captured recitation.
     * @param range Inclusive `[startSample, endSample]` slice to play.
     */
    fun play(audio: RecordedAudio, range: IntRange) {
        if (audio.isEmpty) return
        playRange(audio, range.first, range.last)
    }

    private fun playRange(audio: RecordedAudio, startSample: Int, endSample: Int) {
        val start = startSample.coerceIn(0, audio.samples.size - 1)
        val end = endSample.coerceIn(start, audio.samples.size - 1)
        val pcm = toPcm16(audio.samples, start, end)
        if (pcm.isEmpty()) return
        stop()

        val track = createTrack(audio.sampleRate, pcm.size) ?: return
        audioTrack = track
        playJob = scope.launch {
            try {
                track.play()
                var offset = 0
                while (isActive && offset < pcm.size) {
                    val written = track.write(pcm, offset, pcm.size - offset)
                    if (written <= 0) break
                    offset += written
                }
            } catch (e: Exception) {
                // Best-effort playback.
            } finally {
                releaseTrack(track)
                if (audioTrack === track) audioTrack = null
            }
        }
    }

    /** Stops any in-progress playback and releases the track. Idempotent. */
    fun stop() {
        playJob?.cancel()
        playJob = null
        audioTrack?.let { releaseTrack(it) }
        audioTrack = null
    }

    /** Releases playback resources. Call on screen exit. */
    fun release() {
        stop()
    }

    private fun createTrack(sampleRate: Int, sizeShorts: Int): AudioTrack? {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null
        val bufferBytes = maxOf(minBuffer, sizeShorts * Short.SIZE_BYTES)
        return try {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
                AudioTrack.MODE_STREAM
            ).takeIf { it.state == AudioTrack.STATE_INITIALIZED }
        } catch (e: Exception) {
            null
        }
    }

    private fun releaseTrack(track: AudioTrack) {
        runCatching { if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop() }
        runCatching { track.release() }
    }

    private fun toPcm16(samples: FloatArray, start: Int, end: Int): ShortArray {
        val length = end - start + 1
        if (length <= 0) return ShortArray(0)
        val out = ShortArray(length)
        for (i in 0 until length) {
            val clamped = samples[start + i].coerceIn(-1f, 1f)
            out[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
