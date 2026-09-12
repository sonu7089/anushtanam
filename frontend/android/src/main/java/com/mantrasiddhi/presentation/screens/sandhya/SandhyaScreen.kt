/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.sandhya

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.mantrasiddhi.presentation.components.LotusArt
import com.mantrasiddhi.presentation.components.PageIntro
import com.mantrasiddhi.presentation.components.LotusTopAppBar
import com.mantrasiddhi.presentation.components.LotusCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.domain.model.SandhyaCountMode
import com.mantrasiddhi.domain.model.minimumJapa
import com.mantrasiddhi.domain.util.SandhyaDateKey
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.math.BigInteger
import java.text.NumberFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.model.SandhyaAdherence
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.TrendPoint
import com.mantrasiddhi.presentation.components.ProgressRing
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandhyaScreen(
    viewModel: SandhyaViewModel,
    modifier: Modifier = Modifier,
    countViewModel: SandhyaCountViewModel = hiltViewModel(),
    onOpenGuide: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    reminderTarget: com.mantrasiddhi.presentation.navigation.SandhyaReminderTarget? = null,
    onReminderHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by countViewModel.editor.collectAsStateWithLifecycle()
    val minimums by countViewModel.minimums.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerForDraft by remember { mutableStateOf(false) }

    LaunchedEffect(reminderTarget, uiState.loading) {
        val target = reminderTarget ?: return@LaunchedEffect
        if (uiState.loading) return@LaunchedEffect
        viewModel.selectDate(target.date)
        countViewModel.openFromReminder(target.period, target.date, target.userId)
        onReminderHandled()
    }

    LaunchedEffect(countViewModel) {
        countViewModel.saved.collect { change ->
            val feedback = change.feedback
            val note = when {
                feedback.completionReversed -> context.getString(R.string.japa_completion_corrected)
                feedback.purashcharanaCompleted -> context.getString(R.string.japa_vow_complete)
                feedback.targetCompleted -> context.getString(R.string.japa_target_complete)
                feedback.milestonesCrossed > 0 -> context.getString(R.string.japa_milestones_crossed, feedback.milestonesCrossed)
                else -> ""
            }
            val result = snackbar.showSnackbar(
                context.getString(if (change.quantity == 1L) R.string.sandhya_count_saved_one else R.string.sandhya_count_saved, countText(change.quantity),
                    context.getString(periodNameRes(change.period))) + if (note.isEmpty()) "" else ". $note",
                actionLabel = context.getString(R.string.sandhya_edit_total),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                countViewModel.open(change.period, SandhyaDateKey.parse(change.dateKey), SandhyaCountMode.EDIT)
            }
        }
    }
    LaunchedEffect(countViewModel) {
        countViewModel.errors.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LotusTopAppBar(
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.sandhya_settings_open))
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.title_sandhya),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                PageIntro(stringResource(R.string.lotus_sandhya_intro),
                    stringResource(R.string.sandhya_intro), LotusArt.RIVER)
                Spacer(Modifier.height(16.dp))

                com.mantrasiddhi.presentation.screens.guide.PracticeGuideReminder(onOpenGuide)
                Spacer(Modifier.height(16.dp))
                uiState.draft?.let { draft ->
                    SandhyaDraftCard(draft, onResume = {
                        countViewModel.open(draft.period, SandhyaDateKey.parse(draft.dateKey), draft.mode)
                    })
                    Spacer(Modifier.height(16.dp))
                }
                if (!uiState.loading) {
                    TodayCard(today = uiState.today, onMarkDone = viewModel::markObservedToday,
                        date = uiState.date,
                        canMarkObserved = uiState.isToday,
                        onSelectDate = { datePickerForDraft = false; showDatePicker = true },
                        onRecord = { period, mode -> countViewModel.open(period, uiState.date, mode) })
                    Spacer(Modifier.height(16.dp))
                }

                MetricsSection(adherence = uiState.adherence)
                if (uiState.enabled && (uiState.preparationEnabled || uiState.eventEnabled)) {
                    Spacer(Modifier.height(24.dp))
                    SandhyaNextReminders(uiState.enabled, uiState.preparationEnabled, uiState.eventEnabled,
                        uiState.nextReminders, uiState.reminderZoneId)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    editor?.let { state ->
        SandhyaCountSheet(state, countViewModel::changeInput, countViewModel::step,
            countViewModel::togglePause, countViewModel::save, countViewModel::discard,
            countViewModel::dismiss, countViewModel::undo,
            onChooseDate = { datePickerForDraft = true; showDatePicker = true },
            minimumCount = minimums[state.draft.period])
    }
    if (showDatePicker) {
        val draftDate = editor?.draft?.takeIf { datePickerForDraft }
        val latestDate = LocalDate.now(runCatching { java.time.ZoneId.of(draftDate?.zoneId ?: uiState.region.zoneId) }
            .getOrDefault(java.time.ZoneId.systemDefault()))
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (draftDate?.let { SandhyaDateKey.parse(it.dateKey) } ?: uiState.date)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(latestDate)
                override fun isSelectableYear(year: Int) = year <= latestDate.year
            }
        )
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let {
                    val date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    if (draftDate != null) countViewModel.changeDate(date)
                    else viewModel.selectDate(date.takeUnless { it == latestDate })
                }
                showDatePicker = false
            }, enabled = picker.selectedDateMillis != null) { Text(stringResource(R.string.sandhya_show_date)) }
        }, dismissButton = {
            TextButton(onClick = {
                if (draftDate != null) countViewModel.changeDate(latestDate) else viewModel.selectDate(null)
                showDatePicker = false
            }) {
                Text(stringResource(R.string.sandhya_today))
            }
        }) { DatePicker(state = picker) }
    }


}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayCard(
    today: List<SandhyaTodayItem>,
    onMarkDone: (SandhyaPeriod) -> Unit,
    date: LocalDate = LocalDate.now(),
    remindersEnabled: Boolean = false,
    canMarkObserved: Boolean = true,
    preparationEnabled: Boolean = true,
    eventEnabled: Boolean = true,
    onSelectDate: () -> Unit = {},
    onRecord: (SandhyaPeriod, SandhyaCountMode) -> Unit = { _, _ -> }
) {
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    LotusCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SandhyaHeaderRow(
                title = {
                    Column {
                        Text(stringResource(R.string.sandhya_today_title), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("sandhya-title"))
                        val recorded = today.mapNotNull { it.count }
                        if (recorded.isNotEmpty()) {
                            val total = recorded.fold(BigInteger.ZERO) { sum, count -> sum + BigInteger.valueOf(count) }
                            Text(mantraCountText(total), style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                action = {
                    TextButton(onClick = onSelectDate, contentPadding = PaddingValues(vertical = 12.dp)) {
                        Text(practiceDateText(SandhyaDateKey.of(date)), Modifier.testTag("sandhya-date"))
                    }
                }
            )
            today.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SandhyaHeaderRow(
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(periodNameRes(item.period)), style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium, modifier = Modifier.testTag("period-${item.period.name}"))
                                Text(item.eventTime?.let { "${stringResource(item.eventLabelRes)} · ${formatter.format(it)}" }
                                    ?: stringResource(R.string.sandhya_polar_none),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (remindersEnabled && !item.observedToday && eventEnabled) item.eventTime?.let {
                                    Text(stringResource(R.string.reminder_today_event, formatter.format(it)),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (remindersEnabled && !item.observedToday && preparationEnabled) item.reminderTime?.let {
                                    Text(stringResource(R.string.reminder_today_prepare, formatter.format(it)),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        action = {
                            if (item.observedToday) {
                                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp))
                                    Text(stringResource(R.string.sandhya_observed), style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.testTag("observance-${item.period.name}"))
                                }
                            } else if (canMarkObserved) {
                                TextButton(onClick = { onMarkDone(item.period) }, contentPadding = PaddingValues(vertical = 12.dp)) {
                                    Text(stringResource(R.string.sandhya_mark_observed),
                                        Modifier.testTag("observance-${item.period.name}"))
                                }
                            }
                        }
                    )
                    SandhyaHeaderRow(
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.count?.let { mantraCountText(it) } ?: stringResource(R.string.sandhya_count_unknown),
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.sandhya_minimum, countText(item.minimumCount)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        action = {
                            if (item.count != null) TextButton(onClick = { onRecord(item.period, SandhyaCountMode.EDIT) },
                                contentPadding = PaddingValues(vertical = 12.dp)) {
                                Text(stringResource(R.string.sandhya_edit_total))
                            }
                        }
                    )
                    FilledTonalButton(onClick = { onRecord(item.period, SandhyaCountMode.COUNTER) }) {
                        Text(stringResource(R.string.sandhya_start_mental))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MetricsSection(adherence: SandhyaAdherence) {
    LotusCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.sandhya_metrics_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))

            if (adherence.totalScheduled == 0) {
                Text(
                    text = stringResource(R.string.sandhya_metrics_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp)) {
                    ProgressRing(
                        progress = adherence.overallAdherenceRate.toFloat().coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp
                    ) {
                        Text(
                            text = stringResource(
                                R.string.sandhya_percent,
                                (adherence.overallAdherenceRate * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.sandhya_adherence_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.sandhya_total_observed, adherence.totalObserved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val punctuality = punctualityText(adherence)
                    if (punctuality.isNotEmpty()) {
                        Text(
                            text = punctuality,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreakStat(
                    label = stringResource(R.string.sandhya_streak_full_label),
                    value = adherence.currentStreakAllThree,
                    best = adherence.longestStreakAllThree,
                    modifier = Modifier.weight(1f)
                )
                StreakStat(
                    label = stringResource(R.string.sandhya_streak_any_label),
                    value = adherence.currentStreakAtLeastOne,
                    best = adherence.longestStreakAtLeastOne,
                    modifier = Modifier.weight(1f)
                )
            }

            adherence.mostConsistentPeriod?.let { period ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.sandhya_most_consistent,
                        stringResource(periodNameRes(period))
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.sandhya_per_period_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            adherence.perPeriod.forEach { pa ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(periodNameRes(pa.period)), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.sandhya_per_period_value,
                            pa.observed, pa.scheduled, (pa.rate * 100).roundToInt()),
                            Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { pa.rate.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
            }

            if (adherence.last7Days.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.sandhya_trend_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                WeeklyTrend(points = adherence.last7Days)
            }
        }
    }
}

@Composable
private fun StreakStat(label: String, value: Int, best: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.sandhya_streak_days, value),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.sandhya_streak_best, best),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun WeeklyTrend(points: List<TrendPoint>) {
    // Scale to the largest recorded or scheduled count. Imported/extra observations
    // still have a truthful bar, even when they exceed the original schedule.
    val maximum = points.maxOfOrNull { maxOf(it.scheduled, it.observed, 1) } ?: 1
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEach { point ->
            val fraction = (point.observed.toFloat() / maximum).coerceIn(0f, 1f)
            val announcement = stringResource(R.string.sandhya_trend_day_description,
                point.dateKey, point.observed, point.scheduled)
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = announcement
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(72.dp).testTag("trend-lane-${point.dateKey}"),
                    contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height((72 * fraction).dp.coerceAtLeast(2.dp))
                        .testTag("trend-bar-${point.dateKey}")
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (point.observed > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant))
                }
                Spacer(Modifier.height(8.dp))
                Text(point.dateKey.takeLast(2), Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun periodNameRes(period: SandhyaPeriod): Int = when (period) {
    SandhyaPeriod.PRATAH -> R.string.sandhya_period_pratah
    SandhyaPeriod.MADHYAHNA -> R.string.sandhya_period_madhyahna
    SandhyaPeriod.SAYAM -> R.string.sandhya_period_sayam
}

@Composable
private fun punctualityText(adherence: SandhyaAdherence): String {
    val delta = adherence.avgPunctualityDeltaMinutes.roundToInt()
    return when {
        adherence.totalObserved == 0 -> ""
        delta in -1..1 -> stringResource(R.string.sandhya_punctuality_ontime)
        delta < 0 -> stringResource(R.string.sandhya_punctuality_early, -delta)
        else -> stringResource(R.string.sandhya_punctuality_late, delta)
    }
}
