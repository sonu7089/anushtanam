/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.audio

import android.content.Context
import android.media.MediaPlayer
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
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
     * Timestamps mapping the words of the Gayatri Mantra in the benchmark audio.
     *
     * The word list and canonical timings come from [GayatriMantra] (single source
     * of truth). Parsing of `assets/benchmark_timestamps.json` stays in this data
     * layer and, when present, refines the per-word start/end values.
     */
    val wordTimestamps = loadWordTimestamps()

    private fun loadWordTimestamps(): List<WordTimestamp> {
        val overrides = parseTimestampOverrides()
        return GayatriMantra.words.map { word ->
            val override = overrides[word.index]
            WordTimestamp(
                wordIndex = word.index,
                word = word.transliteration,
                startMs = override?.first ?: word.benchmarkStartMs,
                endMs = override?.second ?: word.benchmarkEndMs
            )
        }
    }

    /**
     * Parses optional per-word timing overrides from the bundled asset.
     * Returns an empty map on any parsing failure so the canonical defaults apply.
     */
    private fun parseTimestampOverrides(): Map<Int, Pair<Long, Long>> {
        return try {
            val jsonString = context.assets.open("benchmark_timestamps.json")
                .bufferedReader()
                .use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            val map = mutableMapOf<Int, Pair<Long, Long>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val wordIndex = obj.getInt("wordIndex")
                // Convert centiseconds to milliseconds
                val startMs = obj.getLong("startMs") * 10L
                val endMs = obj.getLong("endMs") * 10L
                map[wordIndex] = startMs to endMs
            }
            map
        } catch (e: Exception) {
            emptyMap()
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
        playRange(startMs, endMs)
    }

    /**
     * Plays *exactly* the selected word from the benchmark recording (Phase 4.2
     * "tap to hear"): the playback window is that word's own
     * `benchmarkStartMs..benchmarkEndMs`, with no neighbouring words.
     *
     * @param wordIndex Zero-based reference word index to hear.
     */
    fun playWord(wordIndex: Int) {
        val timestamp = wordTimestamps.getOrNull(wordIndex) ?: return
        playRange(timestamp.startMs, timestamp.endMs)
    }

    /**
     * Seeks to [startMs] and plays until [endMs], lazily creating the player.
     *
     * The seek target is clamped into the player's valid range and the segment end
     * is clamped to the media duration, so tapping the final word (whose `endMs` may
     * run past the end of the recording) can never seek out of bounds. All player
     * calls are wrapped so a bad seek/start can never crash the app.
     */
    private fun playRange(startMs: Long, endMs: Long) {
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
        val player = mediaPlayer ?: return
        try {
            val duration = player.duration
            // Clamp the seek target to [0, duration-1]; MediaPlayer.duration can be
            // -1 for an unprepared/mock player, in which case we fall back to 0.
            val maxSeek = if (duration > 0) (duration - 1).toLong() else 0L
            val safeStart = startMs.coerceIn(0L, maxSeek)
            // Clamp the segment end to the media duration and guard against an end
            // at/behind the (clamped) start.
            val safeEnd = if (duration > 0) endMs.coerceAtMost(duration.toLong()) else endMs
            segmentEndMs = if (safeEnd > safeStart) safeEnd else null
            player.seekTo(safeStart.toInt())
            player.start()
            _isPlaying.value = true
            startProgressTracker()
        } catch (e: Exception) {
            // A bad seek/start must never crash the app.
            segmentEndMs = null
        }
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
