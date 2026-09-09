package com.mantrasiddhi.review

import androidx.compose.runtime.*
import com.mantrasiddhi.data.preferences.SandhyaSettings
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.presentation.screens.settings.ReminderPreferencesCard
import java.time.LocalDate

@Composable
internal fun ReminderReview(blocked: Boolean = false, summary: Boolean = false) {
    var settings by remember { mutableStateOf(SandhyaSettings(true, SandhyaRegions.DEFAULT, 1)) }
    val next = remember { buildMap {
        SandhyaPeriod.entries.forEachIndexed { index, period ->
            val event = LocalDate.of(2026, 9, 10).atTime(6 + index * 6, 20).atZone(settings.zone()).toInstant().toEpochMilli()
            put(period to SandhyaReminderStage.PREPARATION, SandhyaReminderTime(event, event - 900_000))
            put(period to SandhyaReminderStage.EVENT, SandhyaReminderTime(event, event))
        }
    } }
    if (summary) {
        com.mantrasiddhi.presentation.screens.sandhya.SandhyaNextReminders(settings.enabled,
            settings.preparationEnabled, settings.eventEnabled, next, settings.zone().id)
        return
    }
    ReminderPreferencesCard(settings,
        notificationsAllowed = !blocked,
        onEnabled = { settings = settings.copy(enabled = it) },
        onStage = { stage, enabled -> settings = if (stage == SandhyaReminderStage.PREPARATION)
            settings.copy(preparationEnabled = enabled) else settings.copy(eventEnabled = enabled) })
}

@Composable
internal fun SandhyaSettingsReview() {
    com.mantrasiddhi.presentation.screens.settings.SandhyaSettingsContent(onBack = {},
        regionContent = { com.mantrasiddhi.presentation.screens.settings.RegionCard("New Delhi", {}, {}) },
        reminderContent = { ReminderReview() })
}
