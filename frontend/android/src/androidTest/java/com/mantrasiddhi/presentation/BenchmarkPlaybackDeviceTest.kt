package com.mantrasiddhi.presentation

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.audio.BenchmarkAudioPlayer
import com.mantrasiddhi.data.mantra.MantraBenchmarkResources
import com.mantrasiddhi.domain.mantra.BundledMantraCatalog
import org.junit.*
import org.junit.Assert.*

/** Uses the bundled recording and real Android MediaPlayer; no microphone or saved sessions. */
class BenchmarkPlaybackDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var player: BenchmarkAudioPlayer
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    @Before fun setup() = main {
        val context = instrumentation.targetContext
        player = BenchmarkAudioPlayer(context, BundledMantraCatalog.default, MantraBenchmarkResources(context))
    }
    @After fun cleanup() = main { player.release() }
    private fun awaitStopped() {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (player.isPlaying.value && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
        assertFalse("Segment must stop", player.isPlaying.value)
    }

    @Test fun firstMiddleAndLastWordStopWithinOneSamplingWindowPlusDeviceScheduling() {
        for (index in listOf(0, player.wordTimestamps.size / 2, player.wordTimestamps.lastIndex)) {
            main { player.playWord(index) }
            assertTrue(player.isPlaying.value)
            awaitStopped()
            val position = player.currentPositionMs.value
            // Completion rewinds to zero when the selected final word reaches media end.
            if (position != 0L) {
                val end = player.wordTimestamps[index].endMs
                assertTrue("Word $index stopped at $position, expected $end..${end + 150}", position in end..end + 150)
            }
        }
    }

    @Test fun pauseResumeReplacementAndReleaseLeaveNoRunningTracker() {
        main { player.play() }
        SystemClock.sleep(600)
        main { player.pause() }
        val paused = player.currentPositionMs.value
        assertTrue(paused > 0)
        SystemClock.sleep(250)
        assertEquals(paused, player.currentPositionMs.value)
        main { player.playWord(1); player.playWord(3) }
        awaitStopped()
        main { player.play(); player.release() }
        SystemClock.sleep(250)
        assertFalse(player.isPlaying.value)
        assertEquals(0L, player.currentPositionMs.value)
        assertNull(player.activeWordIndex.value)
    }
}
