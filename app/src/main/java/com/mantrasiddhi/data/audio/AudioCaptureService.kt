/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.mantrasiddhi.domain.evaluation.acoustic.RecordedAudio
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures the learner's raw microphone audio (16 kHz mono PCM) **in parallel** with
 * `SpeechRecognizer`, which does not expose its raw audio.
 *
 * The recogniser holds the mic on many devices, so this service is best-effort: if
 * `AudioRecord` cannot initialise or read (mic contention, permission, unusual
 * hardware) it simply yields an empty buffer and the evaluation degrades to the
 * text-only path — it never throws and never blocks the speech flow.
 *
 * Lifecycle mirrors [BenchmarkAudioPlayer]: explicit [start]/[stop], a dedicated
 * read coroutine, and idempotent release of the `AudioRecord` so the mic is never
 * leaked on screen exit.
 */
@Singleton
class AudioCaptureService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var buffer: PcmBuffer? = null

    /** Whether a capture session is currently active. */
    @Volatile
    var isCapturing: Boolean = false
        private set

    /**
     * Begins capturing audio into a fresh [PcmBuffer]. Safe to call when capture is
     * already running (no-op) or when the mic is unavailable (silently degrades).
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isCapturing) return
        val minBuffer = AudioRecord.getMinBufferSize(
            PcmBuffer.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return

        val readSize = maxOf(minBuffer, READ_CHUNK_SAMPLES)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                PcmBuffer.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                readSize * Short.SIZE_BYTES
            )
        } catch (e: Exception) {
            null
        }
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record?.release() }
            return
        }

        val pcm = PcmBuffer(PcmBuffer.SAMPLE_RATE)
        audioRecord = record
        buffer = pcm
        isCapturing = true

        recordJob = scope.launch {
            val chunk = ShortArray(readSize)
            try {
                record.startRecording()
                while (isActive && isCapturing) {
                    val read = record.read(chunk, 0, chunk.size)
                    if (read > 0) pcm.append(chunk, read)
                }
            } catch (e: Exception) {
                // Best-effort capture; leave whatever was buffered.
            }
        }
    }

    /**
     * Stops capture and returns the captured audio (possibly empty), releasing the
     * `AudioRecord`. Idempotent.
     *
     * @return The captured [RecordedAudio]; empty when capture never produced data.
     */
    fun stop(): RecordedAudio {
        isCapturing = false
        recordJob?.cancel()
        recordJob = null
        audioRecord?.apply {
            runCatching { if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop() }
            runCatching { release() }
        }
        audioRecord = null
        val captured = buffer?.toRecordedAudio() ?: RecordedAudio(FloatArray(0), PcmBuffer.SAMPLE_RATE)
        buffer = null
        return captured
    }

    /** Releases any active capture without returning data. Call on screen exit. */
    fun release() {
        stop()
    }

    private companion object {
        /** Preferred read block size in samples (~64 ms at 16 kHz). */
        const val READ_CHUNK_SAMPLES = 1024
    }
}
