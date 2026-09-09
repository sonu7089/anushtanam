/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.sandhya

import com.mantrasiddhi.presentation.components.LotusArt
import com.mantrasiddhi.presentation.components.PageIntro
import com.mantrasiddhi.presentation.components.LotusTopAppBar
import com.mantrasiddhi.presentation.components.LotusCard
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.ContextCompat
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
    onOpenGuide: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val editor by countViewModel.editor.collectAsState()
    val minimums by countViewModel.minimums.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerForDraft by remember { mutableStateOf(false) }
    var showRegionDialog by remember { mutableStateOf(false) }

    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        viewModel.setEnabled(true) // schedule regardless; the notifier no-ops until granted
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.useMyLocation()
        } else {
            Toast.makeText(context, R.string.sandhya_location_denied, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { resId ->
            Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
        }
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
                        date = uiState.date, remindersEnabled = uiState.enabled && uiState.isToday,
                        canMarkObserved = uiState.isToday,
                        onSelectDate = { datePickerForDraft = false; showDatePicker = true },
                        onRecord = { period, mode -> countViewModel.open(period, uiState.date, mode) })
                    Spacer(Modifier.height(16.dp))
                }

                RegionCard(
                    regionName = uiState.region.displayName.ifBlank {
                        stringResource(R.string.sandhya_region_current)
                    },
                    onChangeClick = { showRegionDialog = true },
                    onUseLocationClick = {
                        if (viewModel.locationPermissionGranted()) {
                            viewModel.useMyLocation()
                        } else {
                            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))

                ReminderToggleCard(
                    enabled = uiState.enabled,
                    notificationsBlocked = uiState.enabled && !notificationsGranted,
                    onToggle = { wantEnabled ->
                        if (wantEnabled && !notificationsGranted &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setEnabled(wantEnabled)
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))

                MetricsSection(adherence = uiState.adherence)
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

    if (showRegionDialog) {
        val searchState by viewModel.searchState.collectAsState()
        RegionPickerDialog(
            searchState = searchState,
            onSearch = viewModel::searchLocations,
            onSelectPlace = { place ->
                viewModel.selectSearchedPlace(place)
                showRegionDialog = false
            },
            onDismiss = {
                viewModel.clearLocationSearch()
                showRegionDialog = false
            }
        )
    }
}

@Composable
private fun RegionCard(
    regionName: String,
    onChangeClick: () -> Unit,
    onUseLocationClick: () -> Unit
) {
    LotusCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.sandhya_region_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = regionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onChangeClick, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sandhya_change))
                }
                FilledTonalButton(onClick = onUseLocationClick, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sandhya_use_location))
                }
            }
        }
    }
}

@Composable
private fun ReminderToggleCard(
    enabled: Boolean,
    notificationsBlocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    LotusCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sandhya_enable_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.sandhya_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (notificationsBlocked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.sandhya_notif_perm_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
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
                                if (remindersEnabled) item.reminderTime?.let {
                                    Text(stringResource(R.string.sandhya_reminder_at, formatter.format(it)),
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

/**
 * Free-text location search dialog: the user types any town/place, the platform
 * geocoder resolves candidates, and tapping one sets it as the Sandhya region.
 * Replaces the earlier fixed city list.
 */
@Composable
private fun RegionPickerDialog(
    searchState: LocationSearchState,
    onSearch: (String) -> Unit,
    onSelectPlace: (com.mantrasiddhi.data.location.GeocodingService.GeocodedPlace) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sandhya_select_region_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.sandhya_search_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSearch(query) }
                    ),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { onSearch(query) }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.sandhya_search_action)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (searchState) {
                        is LocationSearchState.Idle -> {
                            Text(
                                text = stringResource(R.string.sandhya_search_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        is LocationSearchState.Searching -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.sandhya_searching),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        is LocationSearchState.NoResults -> {
                            Text(
                                text = stringResource(R.string.sandhya_search_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        is LocationSearchState.Error -> {
                            Text(
                                text = stringResource(R.string.sandhya_search_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        is LocationSearchState.Results -> {
                            searchState.places.forEach { place ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { onSelectPlace(place) }
                                        .padding(vertical = 12.dp, horizontal = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = place.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.target_dialog_cancel)) }
        }
    )
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
