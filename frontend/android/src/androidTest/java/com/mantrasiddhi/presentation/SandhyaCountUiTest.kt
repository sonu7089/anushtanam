package com.mantrasiddhi.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import com.mantrasiddhi.presentation.screens.settings.SandhyaMinimumSettings
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.presentation.screens.sandhya.*
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import com.mantrasiddhi.resources.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class SandhyaCountUiTest {
    @get:Rule val compose = createComposeRule()
    private fun draft(mode: SandhyaCountMode) = SandhyaCountDraft("test", 7, "20260907",
        SandhyaPeriod.SAYAM, "Asia/Kolkata", mode, "600", 100, 1, 1)

    @Test fun directEntryAndCorrectionStayAbsoluteAndRejectInvalidInput() {
        val state = mutableStateOf(SandhyaCountEditor(draft(SandhyaCountMode.EDIT)))
        var saved: Long? = null
        compose.setContent { AnushtanamTheme(false) {
            SandhyaCountSheet(state.value, { state.value = state.value.copy(draft = state.value.draft.copy(input = it)) },
                {}, {}, { saved = state.value.draft.newTotal }, {}, {})
        } }
        compose.onNodeWithTag("sandhya-count-input").performTextReplacement("-1")
        compose.onNodeWithTag("sandhya-count-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("sandhya-count-input").performScrollTo().performTextReplacement("600")
        compose.onNodeWithText("Period total: 600").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("sandhya-count-save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(600L, saved) }
        state.value = state.value.copy(saving = true)
        compose.onNodeWithTag("sandhya-count-save").assertIsNotEnabled()
    }

    @Test fun countsNeverMarkPrayerObservedAndAllActionsFitLargeText() {
        var observed = 0
        val modes = mutableListOf<SandhyaCountMode>()
        compose.setContent { AnushtanamTheme(false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    TodayCard(listOf(SandhyaTodayItem(SandhyaPeriod.SAYAM, R.string.sandhya_event_sunset,
                        LocalTime.of(18, 0), null, false, 600)), { observed++ }, onRecord = { _, mode -> modes += mode })
                }
            }
        } }
        compose.onNodeWithText("Add more").assertDoesNotExist()
        compose.onNodeWithText("Record count").assertDoesNotExist()
        compose.onNodeWithText("Edit total").performScrollTo().performClick()
        compose.onNodeWithText("Start mental Japa").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(SandhyaCountMode.EDIT, SandhyaCountMode.COUNTER), modes)
            assertEquals(0, observed)
        }
        compose.onNodeWithText("Mark observed").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, observed) }
    }

    @Test fun pausedCounterCannotAddAndCanSaveRecoveredQuantity() {
        var taps = 0
        var saved: Long? = null
        val state = mutableStateOf(SandhyaCountEditor(draft(SandhyaCountMode.COUNTER).copy(input = "57", baseQuantity = null), paused = true))
        compose.setContent { AnushtanamTheme(true) {
            SandhyaCountSheet(state.value, {}, { taps++ }, { state.value = state.value.copy(paused = false) },
                { saved = state.value.draft.newTotal }, {}, {})
        } }
        compose.onNodeWithTag("sandhya-counter-tap").assertIsNotEnabled()
        compose.onNodeWithTag("sandhya-count-save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(57L, saved); assertEquals(0, taps) }
        compose.onNodeWithText("Resume").performScrollTo().performClick()
        compose.onNodeWithTag("sandhya-counter-tap").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun counterAllowsDirectEntryAndShowsPersonalMinimum() {
        var saved: Long? = null
        val state = mutableStateOf(SandhyaCountEditor(draft(SandhyaCountMode.COUNTER).copy(input = "0", baseQuantity = null)))
        compose.setContent { AnushtanamTheme(false) {
            SandhyaCountSheet(state.value, { state.value = state.value.copy(draft = state.value.draft.copy(input = it)) },
                {}, {}, { saved = state.value.draft.newTotal }, {}, {}, minimumCount = 200)
        } }
        compose.onNodeWithText("Minimum 200").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit count").performClick()
        compose.onNodeWithTag("sandhya-count-input").performTextReplacement("600")
        compose.onNodeWithTag("sandhya-count-input").performImeAction()
        compose.onNodeWithTag("sandhya-count-save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(600L, saved) }
    }

    @Test fun settingsSaveAllThreeMinimumsAndRejectInvalidInput() {
        val values = mutableStateOf(SandhyaMinimums())
        compose.setContent { AnushtanamTheme(false) {
            Column(Modifier.padding(24.dp)) { SandhyaMinimumSettings(values.value, { values.value = it }) }
        } }
        compose.onNodeWithText("Change").performClick()
        compose.onNodeWithTag("minimum-PRATAH").performTextReplacement("216")
        compose.onNodeWithTag("minimum-MADHYAHNA").performTextReplacement("54")
        compose.onNodeWithTag("minimum-SAYAM").performTextReplacement("-1")
        compose.onNodeWithTag("minimum-save").assertIsNotEnabled()
        compose.onNodeWithTag("minimum-SAYAM").performTextReplacement("600")
        compose.onNodeWithTag("minimum-save").performClick()
        compose.runOnIdle { assertEquals(SandhyaMinimums(216, 54, 600), values.value) }
        compose.onNodeWithTag("minimum-save").assertDoesNotExist()
        compose.onNodeWithText("600").assertIsDisplayed()
    }

    @Test fun dateAndObservanceKeepTheirBaselinesAndTrailingEdges() {
        val observed = mutableStateOf(false)
        compose.setContent { AnushtanamTheme(false) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                TodayCard(listOf(SandhyaTodayItem(SandhyaPeriod.MADHYAHNA, R.string.sandhya_event_noon,
                    LocalTime.NOON, null, observed.value, 1)), { observed.value = true })
            }
        } }
        fun baseline(tag: String): Float {
            val layouts = mutableListOf<TextLayoutResult>()
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return node.fetchSemanticsNode().boundsInRoot.top + layouts.single().firstBaseline
        }
        fun right(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right
        assertEquals(baseline("sandhya-title"), baseline("sandhya-date"), 1f)
        assertEquals(baseline("period-MADHYAHNA"), baseline("observance-MADHYAHNA"), 1f)
        assertEquals(right("sandhya-date"), right("observance-MADHYAHNA"), 1f)
        compose.onAllNodesWithText("1 mantra").assertCountEquals(2)
        compose.onNodeWithText("Mark observed").performClick()
        assertEquals(baseline("period-MADHYAHNA"), baseline("observance-MADHYAHNA"), 1f)
        assertEquals(right("sandhya-date"), right("observance-MADHYAHNA"), 1f)
    }
}
