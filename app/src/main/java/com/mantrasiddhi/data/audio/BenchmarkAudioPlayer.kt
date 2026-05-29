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
    private var segmentEndMs: Long? = null

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
     * Loaded dynamically from the assets configuration file.
     */
    val wordTimestamps = loadWordTimestamps()

    private fun loadWordTimestamps(): List<WordTimestamp> {
        return try {
            val jsonString = context.assets.open("benchmark_timestamps.json")
                .bufferedReader()
                .use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            val list = mutableListOf<WordTimestamp>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val wordIndex = obj.getInt("wordIndex")
                val word = obj.getString("word")
                // Convert centiseconds to milliseconds
                val startMs = obj.getLong("startMs") * 10L
                val endMs = obj.getLong("endMs") * 10L
                list.add(WordTimestamp(wordIndex, word, startMs, endMs))
            }
            list
        } catch (e: Exception) {
            // Fallback default timestamps
            listOf(
                WordTimestamp(0, "om", 0, 3340),
                WordTimestamp(1, "bhur", 3390, 4280),
                WordTimestamp(2, "bhuvah", 4290, 4720),
                WordTimestamp(3, "svah", 4840, 5690),
                WordTimestamp(4, "tatsavitur", 6420, 7220),
                WordTimestamp(5, "varenyam", 7230, 9050),
                WordTimestamp(6, "bhargo", 9400, 10080),
                WordTimestamp(7, "devasya", 10090, 10900),
                WordTimestamp(8, "dhimahi", 10910, 11920),
                WordTimestamp(9, "dhiyo", 12060, 12830),
                WordTimestamp(10, "yo", 12840, 13050),
                WordTimestamp(11, "nah", 13200, 13400),
                WordTimestamp(12, "prachodayat", 13570, 16010)
            )
        }
    }

    /**
     * Starts playing the benchmark audio and the progress tracker.
     */
    fun play() {
        segmentEndMs = null
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
     * Plays a segment of the benchmark audio containing the selected word and its adjacent words.
     */
    fun playSegment(wordIndex: Int) {
        val startWordIndex = (wordIndex - 1).coerceAtLeast(0)
        val endWordIndex = (wordIndex + 1).coerceAtMost(wordTimestamps.size - 1)
        val startMs = wordTimestamps[startWordIndex].startMs
        val endMs = wordTimestamps[endWordIndex].endMs

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
                // Graceful fallback
            }
        }
        segmentEndMs = endMs
        mediaPlayer?.seekTo(startMs.toInt())
        mediaPlayer?.start()
        _isPlaying.value = true
        startProgressTracker()
    }

    /**
     * Pauses the benchmark audio playback.
     */
    fun pause() {
        segmentEndMs = null
        mediaPlayer?.pause()
        _isPlaying.value = false
        stopProgressTracker()
    }

    /**
     * Stops the benchmark audio and resets playback state.
     */
    fun stop() {
        segmentEndMs = null
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
        segmentEndMs = null
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
                
                segmentEndMs?.let { endMs ->
                    if (pos >= endMs) {
                        pause()
                        segmentEndMs = null
                    }
                }
                
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
