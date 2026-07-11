/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.sandhya

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.SandhyaAdherence
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.SandhyaRegion
import com.mantrasiddhi.domain.model.SandhyaRegions
import com.mantrasiddhi.domain.model.TrendPoint
import com.mantrasiddhi.presentation.components.ProgressRing
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandhyaScreen(
    viewModel: SandhyaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
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

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_sandhya),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.sandhya_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

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

                TodayCard(today = uiState.today, onMarkDone = viewModel::markObservedToday)
                Spacer(Modifier.height(16.dp))

                MetricsSection(adherence = uiState.adherence)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showRegionDialog) {
        RegionPickerDialog(
            onDismiss = { showRegionDialog = false },
            onSelect = { region ->
                viewModel.selectRegion(region)
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
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sandhya_region_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
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
                    fontWeight = FontWeight.Bold,
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
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sandhya_enable_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.sandhya_approx_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@Composable
private fun TodayCard(
    today: List<SandhyaTodayItem>,
    onMarkDone: (SandhyaPeriod) -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sandhya_today_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            today.forEach { item ->
                TodayRow(item = item, formatter = formatter, onMarkDone = onMarkDone)
            }
        }
    }
}

@Composable
private fun TodayRow(
    item: SandhyaTodayItem,
    formatter: DateTimeFormatter,
    onMarkDone: (SandhyaPeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(periodNameRes(item.period)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val timeText = item.eventTime?.let { time ->
                "${stringResource(item.eventLabelRes)} · ${formatter.format(time)}"
            } ?: stringResource(R.string.sandhya_polar_none)
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            item.reminderTime?.let { reminder ->
                Text(
                    text = stringResource(R.string.sandhya_reminder_at, formatter.format(reminder)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (item.observedToday) {
            AssistChip(
                onClick = {},
                enabled = false,
                leadingIcon = {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                label = { Text(stringResource(R.string.sandhya_status_done)) }
            )
        } else if (item.eventTime != null) {
            OutlinedButton(onClick = { onMarkDone(item.period) }) {
                Text(stringResource(R.string.sandhya_status_mark))
            }
        }
    }
}

@Composable
private fun MetricsSection(adherence: SandhyaAdherence) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sandhya_metrics_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
                        progress = adherence.overallAdherenceRate.toFloat(),
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp
                    ) {
                        Text(
                            text = stringResource(
                                R.string.sandhya_percent,
                                (adherence.overallAdherenceRate * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.sandhya_adherence_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            adherence.perPeriod.forEach { pa ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(periodNameRes(pa.period)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(120.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LinearProgressIndicator(
                        progress = pa.rate.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.sandhya_per_period_value,
                            pa.observed,
                            pa.scheduled,
                            (pa.rate * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (adherence.last7Days.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.sandhya_trend_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
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
            fontWeight = FontWeight.ExtraBold,
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
private fun WeeklyTrend(points: List<TrendPoint>) {
    val maxScheduled = (points.maxOfOrNull { it.scheduled } ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEach { point ->
            val fraction = if (point.scheduled == 0) 0f else point.observed.toFloat() / maxScheduled
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((60 * fraction).dp.coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = point.dateKey.takeLast(2),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RegionPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (SandhyaRegion) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sandhya_select_region_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SandhyaRegions.all.forEach { region ->
                    Text(
                        text = region.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelect(region) }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.target_dialog_cancel)) }
        }
    )
}

private fun periodNameRes(period: SandhyaPeriod): Int = when (period) {
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
