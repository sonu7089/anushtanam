package com.mantrasiddhi.data.audio

import android.content.Context
import android.media.MediaPlayer
import com.mantrasiddhi.data.mantra.MantraBenchmarkResources
import com.mantrasiddhi.domain.mantra.Mantra
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkAudioPlayerTest {
    private val dispatcher = StandardTestDispatcher()
    private val media = mockk<MediaPlayer>(relaxed = true)
    private var position = 0
    private lateinit var player: BenchmarkAudioPlayer
    private lateinit var completion: MediaPlayer.OnCompletionListener

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = mockk<Context>()
        val mantra = mockk<Mantra>()
        val resources = mockk<MantraBenchmarkResources>()
        every { resources.audioResourceId(mantra) } returns 1
        every { resources.wordTimestamps(mantra) } returns listOf(
            WordTimestamp(0, "one", 0, 125), WordTimestamp(1, "two", 125, 350))
        every { media.duration } returns 400
        every { media.currentPosition } answers { position }
        every { media.seekTo(any<Int>()) } answers { position = firstArg() }
        every { media.setOnCompletionListener(any()) } answers { completion = firstArg() }
        mockkStatic(MediaPlayer::class)
        every { MediaPlayer.create(context, 1) } returns media
        player = BenchmarkAudioPlayer(context, mantra, resources)
    }

    @AfterEach fun cleanup() {
        player.release()
        unmockkStatic(MediaPlayer::class)
        Dispatchers.resetMain()
    }

    @Test fun `word changes retain 50ms checks while cosmetic progress publishes at 200ms`() = runTest(dispatcher) {
        player.play(); runCurrent()
        position = 150
        advanceTimeBy(50); runCurrent()
        assertEquals(1, player.activeWordIndex.value)
        assertEquals(0L, player.currentPositionMs.value)
        position = 200
        advanceTimeBy(150); runCurrent()
        assertEquals(200L, player.currentPositionMs.value)
        player.pause()
    }

    @Test fun `word segment stops between cosmetic updates and pause publishes exact final position`() = runTest(dispatcher) {
        player.playWord(0); runCurrent()
        position = 124
        advanceTimeBy(50); runCurrent()
        assertTrue(player.isPlaying.value)
        position = 130
        advanceTimeBy(50); runCurrent()
        assertFalse(player.isPlaying.value)
        assertEquals(130L, player.currentPositionMs.value)
        verify(exactly = 1) { media.pause() }
        clearMocks(media, answers = false)
        advanceTimeBy(1000); runCurrent()
        verify(exactly = 0) { media.currentPosition }
    }

    @Test fun `replacing segment pause resume completion and release cancel stale trackers`() = runTest(dispatcher) {
        player.playWord(0); runCurrent()
        player.playWord(1); runCurrent()
        assertEquals(125L, player.currentPositionMs.value)
        position = 180
        player.pause()
        assertEquals(180L, player.currentPositionMs.value)
        player.play(); runCurrent()
        position = 360
        advanceTimeBy(50); runCurrent()
        assertTrue(player.isPlaying.value) // Old selected-word boundary was cleared.
        completion.onCompletion(media)
        assertFalse(player.isPlaying.value)
        assertEquals(0L, player.currentPositionMs.value)
        assertNull(player.activeWordIndex.value)
        player.play(); runCurrent()
        player.release()
        clearMocks(media, answers = false)
        advanceTimeBy(1000); runCurrent()
        verify(exactly = 0) { media.currentPosition }
    }
}
