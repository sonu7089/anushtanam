/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.acoustic

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelfAudioSegmenterTest {

    private val sampleRate = 16000

    /** Builds audio with a silent head/tail and a loud voiced middle region. */
    private fun voicedAudio(): RecordedAudio {
        val samples = FloatArray(9600) // 0.6s, 30 windows of 320 samples
        for (i in samples.indices) {
            val window = i / 320
            samples[i] = if (window in 5..24) 0.5f else 0.0f
        }
        return RecordedAudio(samples, sampleRate)
    }

    @Test
    fun should_returnNull_when_audioEmpty() {
        val range = SelfAudioSegmenter.sampleRangeFor(
            RecordedAudio(FloatArray(0), sampleRate),
            listOf(1.0, 1.0),
            wordIndex = 0
        )
        assertNull(range)
    }

    @Test
    fun should_returnNull_when_wordIndexOutOfRange() {
        assertNull(SelfAudioSegmenter.sampleRangeFor(voicedAudio(), listOf(1.0, 1.0), wordIndex = 5))
    }

    @Test
    fun should_segmentWordsInOrder_acrossVoicedSpan() {
        val audio = voicedAudio()
        val durations = listOf(1.0, 1.0)
        val first = SelfAudioSegmenter.sampleRangeFor(audio, durations, wordIndex = 0)
        val second = SelfAudioSegmenter.sampleRangeFor(audio, durations, wordIndex = 1)

        assertNotNull(first)
        assertNotNull(second)
        assertTrue(first!!.first < second!!.first, "word 0 should precede word 1")
        assertTrue(second.last <= audio.samples.size - 1)
    }
}
