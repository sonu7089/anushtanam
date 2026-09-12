package com.mantrasiddhi.presentation

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.domain.diagnostics.WorkProbe
import com.mantrasiddhi.presentation.screens.progress.ProgressContent
import com.mantrasiddhi.presentation.screens.progress.ProgressUiState
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import com.mantrasiddhi.presentation.screens.practice.components.AudioPlayerBar
import org.junit.*
import org.junit.Assert.*

class RenderingWorkUiTest {
    @get:Rule val compose = createComposeRule()
    @After fun cleanup() { WorkProbe.enabled = false }

    @Test fun prolongedLoadingKeepsAnimationOutOfComposition() {
        WorkProbe.enabled = true
        compose.mainClock.autoAdvance = false
        compose.setContent { AnushtanamTheme(false) { Column { ProgressContent(ProgressUiState.Loading) } } }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        val before = WorkProbe.snapshot().first
        repeat(60) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val after = WorkProbe.snapshot().first
        val compositions = (after["render.shimmer.composition"] ?: 0) - (before["render.shimmer.composition"] ?: 0)
        Log.i("RenderingWork", "shimmer compositions over 60 frames=$compositions")
        if (InstrumentationRegistry.getArguments().getString("renderBaseline") != "true") {
            assertEquals("Animation frames must only invalidate draw", 0L, compositions)
        }
    }

    @Test fun playbackProgressAndAccessibleTimeDoNotRecomposePlayerCard() {
        WorkProbe.enabled = true
        val position = mutableLongStateOf(0L)
        val playing = mutableStateOf(false)
        val readPosition = { position.longValue }
        compose.setContent { AnushtanamTheme(false) {
            AudioPlayerBar(playing.value, readPosition, { playing.value = !playing.value })
        } }
        compose.waitForIdle()
        val before = WorkProbe.snapshot().first["render.player.composition"] ?: 0
        repeat(20) { tick ->
            compose.runOnIdle { position.longValue = (tick + 1) * 200L }
            compose.waitForIdle()
        }
        assertEquals(before, WorkProbe.snapshot().first["render.player.composition"])
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(com.mantrasiddhi.resources.R.string.practice_reference_position_format, 4, 16))
            .assertExists()
        compose.onNodeWithContentDescription(context.getString(com.mantrasiddhi.resources.R.string.practice_play_reference_desc)).performClick()
        compose.onNodeWithContentDescription(context.getString(com.mantrasiddhi.resources.R.string.practice_pause_reference_desc)).assertExists()
    }

    @Test fun loadingSharesOneClockAndStopsItWhenHidden() {
        WorkProbe.enabled = true
        lateinit var registry: androidx.lifecycle.LifecycleRegistry
        val owner = object : androidx.lifecycle.LifecycleOwner {
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        compose.runOnUiThread { registry = androidx.lifecycle.LifecycleRegistry(owner).apply {
            currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        } }
        val before = WorkProbe.snapshot().first
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLifecycleOwner provides owner) {
                AnushtanamTheme(true) { Column { ProgressContent(ProgressUiState.Loading) } }
            }
        }
        compose.waitForIdle()
        fun delta(label: String) = (WorkProbe.snapshot().first[label] ?: 0) - (before[label] ?: 0)
        assertEquals(1L, delta("render.shimmer.clock.start"))
        compose.runOnIdle { registry.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertEquals(1L, delta("render.shimmer.clock.stop"))
        compose.runOnIdle { registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertEquals(2L, delta("render.shimmer.clock.start"))
    }
}
