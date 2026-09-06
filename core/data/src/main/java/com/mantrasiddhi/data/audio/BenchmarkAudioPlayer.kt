/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.audio

import android.content.Context
import android.media.MediaPlayer
import com.mantrasiddhi.data.mantra.MantraBenchmarkResources
import com.mantrasiddhi.domain.mantra.ActiveMantra
import com.mantrasiddhi.domain.mantra.Mantra
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
 * Plays the active mantra's benchmark recitation and tracks which word is being spoken.
 *
 * Which recording plays and where each word sits in it both come from the mantra's
 * definition, so the player carries nothing mantra-specific of its own. A mantra with no
 * bundled recording leaves [wordTimestamps] empty and turns every playback call into a
 * no-op, which is what the UI already handles for a player that has not started.
 */
@Singleton
class BenchmarkAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    @ActiveMantra private val mantra: Mantra,
    benchmarkResources: MantraBenchmarkResources
) {
    private val audioResourceId: Int? = benchmarkResources.audioResourceId(mantra)

    private var mediaPlayer: MediaPlayer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var segmentEndMs: Long? = null

    private val _isPlaying = MutableStateFlow(false)

    /** Emits whether audio is currently playing. */
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)

    /** Emits the current playback position in milliseconds. */
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _activeWordIndex = MutableStateFlow<Int?>(null)

    /** Emits the index of the word currently being spoken, or `null`. */
    val activeWordIndex: StateFlow<Int?> = _activeWordIndex.asStateFlow()

    /**
     * Where each word of the mantra sits in the benchmark recording, in word order; empty
     * when the mantra has no benchmark.
     */
    val wordTimestamps: List<WordTimestamp> = benchmarkResources.wordTimestamps(mantra)

    /** Starts playing the benchmark recitation from the beginning. */
    fun play() {
        segmentEndMs = null
        val player = obtainPlayer() ?: return
        player.startTracking()
    }

    /**
     * Plays a window of the recording containing the selected word and its neighbours.
     *
     * @param wordIndex Zero-based reference word index to hear in context.
     */
    fun playSegment(wordIndex: Int) {
        if (wordTimestamps.isEmpty()) return
        val start = wordTimestamps[(wordIndex - 1).coerceIn(wordTimestamps.indices)].startMs
        val end = wordTimestamps[(wordIndex + 1).coerceIn(wordTimestamps.indices)].endMs
        playRange(start, end)
    }

    /**
     * Plays *exactly* the selected word from the benchmark recording ("tap to hear"): the
     * playback window is that word's own `startMs..endMs`, with no neighbouring words.
     *
     * @param wordIndex Zero-based reference word index to hear.
     */
    fun playWord(wordIndex: Int) {
        val timestamp = wordTimestamps.getOrNull(wordIndex) ?: return
        playRange(timestamp.startMs, timestamp.endMs)
    }

    /** Pauses playback, keeping the current position. */
    fun pause() {
        segmentEndMs = null
        mediaPlayer?.pause()
        _isPlaying.value = false
        stopProgressTracker()
    }

    /** Stops playback and rewinds to the start of the recording. */
    fun stop() {
        segmentEndMs = null
        mediaPlayer?.apply {
            if (isPlaying) stop()
            runCatching { prepare() }.onFailure {
                release()
                mediaPlayer = null
            }
        }
        resetPlaybackState()
    }

    /** Releases the underlying player. Call this on screen exit. */
    fun release() {
        segmentEndMs = null
        mediaPlayer?.release()
        mediaPlayer = null
        resetPlaybackState()
    }

    /**
     * Seeks to [startMs] and plays until [endMs].
     *
     * The seek target is clamped into the player's valid range and the segment end to the
     * media duration, so tapping the final word — whose end may run past the end of the
     * recording — can never seek out of bounds. A bad seek or start leaves playback
     * untouched rather than crashing.
     */
    private fun playRange(startMs: Long, endMs: Long) {
        val player = obtainPlayer() ?: return
        runCatching {
            val duration = player.duration
            // MediaPlayer.duration is -1 for an unprepared or mocked player.
            val maxSeek = if (duration > 0) (duration - 1).toLong() else 0L
            val safeStart = startMs.coerceIn(0L, maxSeek)
            val safeEnd = if (duration > 0) endMs.coerceAtMost(duration.toLong()) else endMs
            segmentEndMs = safeEnd.takeIf { it > safeStart }
            player.seekTo(safeStart.toInt())
            player.startTracking()
        }.onFailure { segmentEndMs = null }
    }

    /**
     * Returns the player, creating it on first use. `null` when the mantra has no
     * recording or the media could not be opened.
     */
    private fun obtainPlayer(): MediaPlayer? {
        mediaPlayer?.let { return it }
        val resourceId = audioResourceId ?: return null
        return runCatching {
            MediaPlayer.create(context, resourceId)?.apply {
                setOnCompletionListener {
                    stopProgressTracker()
                    resetPlaybackState()
                }
            }
        }.getOrNull()?.also { mediaPlayer = it }
    }

    private fun MediaPlayer.startTracking() {
        start()
        _isPlaying.value = true
        startProgressTracker()
    }

    private fun resetPlaybackState() {
        _isPlaying.value = false
        stopProgressTracker()
        _currentPositionMs.value = 0L
        _activeWordIndex.value = null
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive) {
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
                _currentPositionMs.value = position
                _activeWordIndex.value = wordTimestamps
                    .find { position in it.startMs until it.endMs }
                    ?.wordIndex

                val end = segmentEndMs
                if (end != null && position >= end) {
                    pause()
                    segmentEndMs = null
                }
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private companion object {
        /** How often playback position and the highlighted word are refreshed. */
        const val PROGRESS_INTERVAL_MS = 50L
    }
}
