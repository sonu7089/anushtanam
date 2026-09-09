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
import com.mantrasiddhi.data.preferences.SandhyaSettings
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.presentation.screens.settings.ReminderPreferencesCard
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SandhyaReminderUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun independentSwitchesStayAccessibleAtLargeText() {
        var settings by mutableStateOf(SandhyaSettings(true, SandhyaRegions.DEFAULT, 7))
        compose.setContent { AnushtanamTheme(false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    ReminderPreferencesCard(settings,
                        onEnabled = { settings = settings.copy(enabled = it) },
                        onStage = { stage, enabled -> settings = if (stage == SandhyaReminderStage.PREPARATION)
                            settings.copy(preparationEnabled = enabled) else settings.copy(eventEnabled = enabled) })
                }
            }
        } }
        compose.onNodeWithText("15 minutes before").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(settings.preparationEnabled); assertTrue(settings.eventEnabled) }
        compose.onNodeWithText("At Sandhya time").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(settings.eventEnabled) }
        compose.onNodeWithText("Choose a reminder above").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remind me each day").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(settings.enabled) }
        compose.onNodeWithText("15 minutes before").assertIsNotEnabled()
        compose.onNodeWithText("Send test notification").assertDoesNotExist()
    }
    @Test fun blockedStatusHasAnActionAndNoTestNotification() {
        var requested = false
        compose.setContent { AnushtanamTheme(true) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReminderPreferencesCard(SandhyaSettings(true, SandhyaRegions.DEFAULT, 7),
                    notificationsAllowed = false, onAllow = { requested = true })
            }
        } }
        compose.onNodeWithText("Notifications are blocked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Allow notifications").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(requested) }
        compose.onNodeWithText("Send test notification").assertDoesNotExist()
    }

    @Test fun upcomingShowsOnlySelectedStagesWithTomorrowDateAndHidesWhenOff() {
        var settings by mutableStateOf(SandhyaSettings(true, SandhyaRegions.DEFAULT, 7, preparationEnabled = true, eventEnabled = false))
        val date = java.time.LocalDate.now(settings.zone()).plusDays(1)
        val event = date.atTime(6, 20).atZone(settings.zone()).toInstant().toEpochMilli()
        val upcoming = mapOf(
            (SandhyaPeriod.PRATAH to SandhyaReminderStage.PREPARATION) to SandhyaReminderTime(event, event - 900_000),
            (SandhyaPeriod.PRATAH to SandhyaReminderStage.EVENT) to SandhyaReminderTime(event, event))
        compose.setContent { AnushtanamTheme(false) {
            com.mantrasiddhi.presentation.screens.sandhya.SandhyaNextReminders(settings.enabled,
                settings.preparationEnabled, settings.eventEnabled, upcoming, settings.zone().id)
        } }
        val formatted = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM").format(date.atTime(6, 5))
        compose.onNodeWithText(formatted).assertIsDisplayed()
        compose.onNodeWithText("6:05 AM").assertIsDisplayed()
        compose.onNodeWithText("Prepare").assertIsDisplayed()
        compose.onAllNodes(hasText("Sandhya time")).assertCountEquals(0)
        compose.runOnIdle { settings = settings.copy(preparationEnabled = false, eventEnabled = true) }
        compose.onAllNodes(hasText("Prepare")).assertCountEquals(0)
        compose.onAllNodes(hasText("Sandhya time")).assertCountEquals(1)
        compose.runOnIdle { settings = settings.copy(eventEnabled = false) }
        compose.onNodeWithText("Next Sandhya reminders").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(enabled = false, preparationEnabled = true) }
        compose.onNodeWithText("Next Sandhya reminders").assertDoesNotExist()
    }

    @Test fun mainSettingsOpensDedicatedPageWithRegionCountsAndReminders() {
        var detail by mutableStateOf(false)
        compose.setContent { AnushtanamTheme(false) {
            if (detail) com.mantrasiddhi.presentation.screens.settings.SandhyaSettingsContent(onBack = { detail = false },
                regionContent = { com.mantrasiddhi.presentation.screens.settings.RegionCard("New Delhi", {}, {}) },
                reminderContent = { ReminderPreferencesCard(SandhyaSettings(true, SandhyaRegions.DEFAULT, 7)) })
            else com.mantrasiddhi.presentation.screens.settings.SettingsContent(null, "LIGHT", "BOTH", {}, {}, {},
                onOpenSandhyaSettings = { detail = true })
        } }
        compose.onNodeWithText("Gayatri Mantra Practice Guide").assertDoesNotExist()
        compose.onNodeWithText("Minimum mantras", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Remind me each day").assertDoesNotExist()
        compose.onNodeWithTag("sandhya-settings-entry").performScrollTo().performClick()
        compose.onNodeWithText("New Delhi").assertIsDisplayed()
        compose.onNodeWithText("Minimum mantras", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remind me each day").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Got it").assertDoesNotExist()
        compose.onNodeWithText("Approximate timing").assertDoesNotExist()
        compose.onNodeWithText("Next Sandhya reminders").assertDoesNotExist()
        compose.onNodeWithTag("sandhya-settings-back").performClick()
        compose.onNodeWithTag("sandhya-settings-entry").performScrollTo().assertIsDisplayed()
    }
}
