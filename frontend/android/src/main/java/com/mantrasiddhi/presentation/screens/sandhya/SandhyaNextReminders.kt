package com.mantrasiddhi.presentation.screens.sandhya

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.presentation.components.LotusCard
import com.mantrasiddhi.resources.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Read-only schedule. Configuration stays on the Sandhya Settings page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SandhyaNextReminders(
    enabled: Boolean,
    preparationEnabled: Boolean,
    eventEnabled: Boolean,
    upcoming: Map<Pair<SandhyaPeriod, SandhyaReminderStage>, SandhyaReminderTime>,
    zoneId: String
) {
    if (!enabled || (!preparationEnabled && !eventEnabled)) return
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE, d MMM") }
    val timeFormat = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    val visible = upcoming.filterKeys { (_, stage) ->
        if (stage == SandhyaReminderStage.PREPARATION) preparationEnabled else eventEnabled
    }
    LotusCard(Modifier.fillMaxWidth().testTag("sandhya-next-reminders")) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.reminder_upcoming), style = MaterialTheme.typography.titleMedium)
            if (visible.isEmpty()) {
                Text(stringResource(R.string.reminder_no_upcoming), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SandhyaPeriod.entries.forEach { period ->
                val stages = SandhyaReminderStage.entries.mapNotNull { stage ->
                    visible[period to stage]?.let { stage to Instant.ofEpochMilli(it.triggerMillis).atZone(zone) }
                }
                if (stages.isNotEmpty()) {
                    val sharedDate = stages.map { it.second.toLocalDate() }.distinct().singleOrNull()
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(periodNameRes(period)), style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp).alignByBaseline())
                            sharedDate?.let {
                                Text(dateFormat.format(it), Modifier.padding(end = 14.dp).alignByBaseline(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val stacked = maxWidth < 260.dp || LocalDensity.current.fontScale > 1.3f
                            val tile: @Composable (Int, Modifier) -> Unit = { index, modifier ->
                                val (stage, dateTime) = stages[index]
                                Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                                    shape = MaterialTheme.shapes.small) {
                                    if (stages.size == 1) {
                                        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(stringResource(if (stage == SandhyaReminderStage.PREPARATION)
                                                R.string.reminder_prepare_label else R.string.reminder_event_label),
                                                Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(timeFormat.format(dateTime), style = MaterialTheme.typography.titleMedium)
                                        }
                                    } else Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(if (stage == SandhyaReminderStage.PREPARATION)
                                            R.string.reminder_prepare_label else R.string.reminder_event_label),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(timeFormat.format(dateTime), style = MaterialTheme.typography.titleMedium)
                                        if (sharedDate == null) Text(dateFormat.format(dateTime),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                stages.indices.forEach { tile(it, Modifier.fillMaxWidth()) }
                            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                stages.indices.forEach { tile(it, Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
