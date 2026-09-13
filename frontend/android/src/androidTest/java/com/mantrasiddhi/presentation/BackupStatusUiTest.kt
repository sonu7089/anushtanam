package com.mantrasiddhi.presentation

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.presentation.screens.settings.*
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupStatusUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quarantinedHistoryExplainsIncompleteCountsAndAllowsRetry() {
        var taps = 0
        compose.setContent { AnushtanamTheme(false) {
            BackupStatusContent(BackupUiState(localOnly = false, attention = true, quarantined = 2), onSyncNow = { taps++ })
        } }
        compose.onNodeWithText("2 cloud records could not be restored.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Counts may be incomplete.", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Sync now").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun statusSitsAboveSignOutWithoutAboutOrUploadControls() {
        compose.setContent { AnushtanamTheme(false) {
            SettingsContent(null, "LIGHT", "BOTH", {}, {}, {}, backupContent = {
                BackupStatusContent(BackupUiState(localOnly = false))
            })
        } }
        compose.onNodeWithTag("settings_sections").performScrollToNode(hasText("Your practice is up to date."))
        compose.onNodeWithText("Your practice is up to date.").assertIsDisplayed()
        compose.onNodeWithText("About Anushtanam").assertDoesNotExist()
        compose.onNodeWithText("Upload").assertDoesNotExist()
        compose.onNodeWithContentDescription("Sync now").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Backup and Sync").assertIsDisplayed()
        compose.onNodeWithText("Sign out").performScrollTo().assertIsDisplayed()
    }

    @Test fun idleManualRefreshDisablesDuringSyncAndCooldown() {
        var state by mutableStateOf(BackupUiState(localOnly = false, waiting = true))
        var taps = 0
        compose.setContent { AnushtanamTheme(false) {
            BackupStatusContent(state, onSyncNow = { taps++ })
        } }
        compose.onNodeWithContentDescription("Sync now").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, taps); state = state.copy(syncing = true) }
        compose.onNodeWithContentDescription("Syncing...").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(syncing = false, manualReady = false) }
        compose.onNodeWithContentDescription("Sync now").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(manualReady = true, waiting = false) }
        compose.onNodeWithContentDescription("Sync now").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, taps) }
    }

    @Test fun conflictChoiceAndAutomaticRetryRemainReadableWithLargeText() {
        var chosen: Boolean? = null
        compose.setContent { AnushtanamTheme(true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                BackupStatusContent(BackupUiState(localOnly = false, attention = true,
                    reviews = listOf(BackupReview("one", "10 Sep 2026", 500, 540, "operation"))),
                    onResolve = { _, alternative -> chosen = alternative })
            }
        } }
        compose.onNodeWithText("Review changed counts").performClick()
        compose.onNodeWithText("Current count: 500").assertIsDisplayed()
        compose.onNodeWithText("Other count: 540").assertIsDisplayed()
        compose.onNodeWithText("Use other count").performClick()
        compose.runOnIdle { assertEquals(true, chosen) }
    }
}
