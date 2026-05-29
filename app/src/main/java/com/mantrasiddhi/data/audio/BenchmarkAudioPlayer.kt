/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.audio

import android.content.Context
import android.media.MediaPlayer
import com.mantrasiddhi.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles playing the benchmark recitation audio and synchronizes word highlighting.
 */
@Singleton
class BenchmarkAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)

    /**
     * StateFlow emitting whether audio is currently playing.
     */
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)

    /**
     * StateFlow emitting the current playback position in milliseconds.
     */
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _activeWordIndex = MutableStateFlow<Int?>(null)

    /**
     * StateFlow emitting the index of the word currently being spoken, or null.
     */
    val activeWordIndex: StateFlow<Int?> = _activeWordIndex.asStateFlow()

    /**
     * Timestamps mapping the 14 words of the Gayatri Mantra in the benchmark audio.
     */
    val wordTimestamps = listOf(
        WordTimestamp(0, "om", 1000, 1600),
        WordTimestamp(1, "bhur", 1600, 2200),
        WordTimestamp(2, "bhuvah", 2200, 2800),
        WordTimestamp(3, "svah", 2800, 3500),
        WordTimestamp(4, "tat", 3800, 4400),
        WordTimestamp(5, "savitur", 4400, 5000),
        WordTimestamp(6, "varenyam", 5000, 5800),
        WordTimestamp(7, "bhargo", 6400, 7100),
        WordTimestamp(8, "devasya", 7100, 8000),
        WordTimestamp(9, "dhimahi", 8000, 8900),
        WordTimestamp(10, "dhiyo", 9400, 10200),
        WordTimestamp(11, "yo", 10200, 11000),
        WordTimestamp(12, "nah", 11000, 11800),
        WordTimestamp(13, "prachodayat", 12200, 15800)
    )

    /**
     * Starts playing the benchmark audio and the progress tracker.
     */
    fun play() {
        if (mediaPlayer == null) {
            try {
                mediaPlayer = MediaPlayer.create(context, R.raw.gayatri_mantra_benchmark)?.apply {
                    setOnCompletionListener {
                        stopProgressTracker()
                        _isPlaying.value = false
                        _currentPositionMs.value = 0L
                        _activeWordIndex.value = null
                    }
                }
            } catch (e: Exception) {
                // Graceful fallback for mock environments or invalid assets
            }
        }
        mediaPlayer?.start()
        _isPlaying.value = true
        startProgressTracker()
    }

    /**
     * Pauses the benchmark audio playback.
     */
    fun pause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        stopProgressTracker()
    }

    /**
     * Stops the benchmark audio and resets playback state.
     */
    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            try {
                prepare()
            } catch (e: Exception) {
                release()
                mediaPlayer = null
            }
        }
        _isPlaying.value = false
        stopProgressTracker()
        _currentPositionMs.value = 0L
        _activeWordIndex.value = null
    }

    /**
     * Releases the MediaPlayer resources. Call this on screen exit.
     */
    fun release() {
        stopProgressTracker()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _activeWordIndex.value = null
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive) {
                val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                _currentPositionMs.value = pos
                updateActiveWordIndex(pos)
                delay(50)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateActiveWordIndex(positionMs: Long) {
        val word = wordTimestamps.find { positionMs in it.startMs until it.endMs }
        _activeWordIndex.value = word?.wordIndex
    }
}
