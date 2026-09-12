package com.mantrasiddhi.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.mantrasiddhi.presentation.components.rememberDeadlineTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeadlineTimeTest {
    @get:Rule val compose = createComposeRule()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun expiryIsExactAndStoppedContentRefreshesOnResume() {
        lateinit var owner: Owner
        var deadline by mutableLongStateOf(0)
        compose.runOnIdle {
            owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED }
            deadline = System.currentTimeMillis() + 1_500
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val now = rememberDeadlineTime(listOf(deadline))
                Text(if (now < deadline) "active" else "expired")
            }
        }
        compose.onNodeWithText("active").assertExists()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("expired").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { deadline = System.currentTimeMillis() + 1_000 }
        compose.onNodeWithText("active").assertExists()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        Thread.sleep(1_200)
        compose.onNodeWithText("active").assertExists()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("expired").assertExists()
    }

    @Test fun publicationAndReplacementCancelTheOldDeadline() {
        val start = System.currentTimeMillis() + 1_500
        var boundaries by mutableStateOf(listOf(start, start + 1_500))
        compose.setContent {
            val now = rememberDeadlineTime(boundaries)
            Text(when { now < boundaries.first() -> "future"; now < boundaries.last() -> "visible"; else -> "expired" })
        }
        compose.onNodeWithText("future").assertExists()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("visible").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { boundaries = listOf(System.currentTimeMillis() - 1, System.currentTimeMillis() + 10_000) }
        Thread.sleep(1_600)
        compose.onNodeWithText("visible").assertExists()
    }
}
