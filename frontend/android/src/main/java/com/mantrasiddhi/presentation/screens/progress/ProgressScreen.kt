/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.progress

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.mantrasiddhi.presentation.components.LotusArt
import com.mantrasiddhi.presentation.components.PageIntro
import com.mantrasiddhi.presentation.components.LotusTopAppBar
import com.mantrasiddhi.presentation.components.AdaptivePair
import com.mantrasiddhi.presentation.components.LotusCard
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.components.EmptyState
import com.mantrasiddhi.presentation.components.SkeletonCardItem
import com.mantrasiddhi.presentation.components.SkeletonSessionItem
import com.mantrasiddhi.presentation.screens.progress.components.AccuracyLineChart
import com.mantrasiddhi.presentation.screens.progress.components.AxisTrendChart
import com.mantrasiddhi.presentation.screens.progress.components.DailyBarChart
import com.mantrasiddhi.presentation.screens.progress.components.SessionHistoryList

/**
 * Main Progress and Analytics Screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            LotusTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.title_progress),
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
            Column(modifier = Modifier.fillMaxSize()) {
                ProgressContent(uiState, selectedTab, viewModel::selectTab, viewModel::retry, viewModel::loadMore)
            }
        }
    }
}

@Composable
private fun ProgressLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        SkeletonCardItem()
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonCardItem()
        Spacer(modifier = Modifier.height(24.dp))
        repeat(3) {
            SkeletonSessionItem()
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProgressErrorScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(id = R.string.progress_error_icon_desc),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun ProgressContent(state: ProgressUiState, selected: Int? = null,
    onSelect: (Int) -> Unit = {}, onRetry: () -> Unit = {}, onMore: () -> Unit = {}) {
    var localTab by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = selected ?: localTab
    val tabState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val tabs = listOf(
        R.string.progress_tab_overview,
        R.string.progress_tab_daily_chart,
        R.string.progress_tab_accuracy,
        R.string.progress_tab_history
    )

    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        edgePadding = 16.dp,
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        tabs.forEachIndexed { index, titleRes ->
            Tab(
                selected = selectedTab == index,
                onClick = { localTab = index; onSelect(index) },
                text = { Text(text = stringResource(titleRes), fontWeight = FontWeight.SemiBold) }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        when (state) {
            ProgressUiState.Loading -> ProgressLoadingSkeleton()
            is ProgressUiState.Error -> Column {
                Text(stringResource(R.string.error_unknown), color = MaterialTheme.colorScheme.error)
                androidx.compose.material3.TextButton(onClick = onRetry) { Text(stringResource(R.string.section_retry)) }
            }
            is ProgressUiState.Success -> Column(Modifier.testTag("perf_progress_ready")) {
                if (state.refreshing) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.error) androidx.compose.material3.TextButton(onClick = if (selectedTab == 3) onMore else onRetry) {
                    Text(stringResource(R.string.section_retry))
                }
                tabState.SaveableStateProvider(selectedTab) {
                    when (selectedTab) {
                        0 -> OverviewTab(state)
                        1 -> DailyChartTab(state)
                        2 -> AccuracyTab(state)
                        3 -> HistoryTab(state, onMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(state: ProgressUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        val stats = state.overviewStats
        PageIntro(stringResource(R.string.lotus_progress_intro),
            stringResource(R.string.lotus_progress_description), LotusArt.RITUAL)

        Text(
            text = stringResource(R.string.progress_activity_summary),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        AdaptivePair(first = { cardModifier ->
            StatCard(
                title = stringResource(R.string.progress_total_recitations),
                value = stats.performedJapa ?: stats.totalCorrectRecitations.toString(),
                subtitle = stringResource(R.string.japa_history_note),
                icon = Icons.Default.Star,
                modifier = cardModifier
            )
        }, second = { cardModifier ->
            StatCard(
                title = stringResource(R.string.progress_average_accuracy),
                value = "${(stats.averageAccuracy * 100).toInt()}%",
                subtitle = stringResource(R.string.progress_overall_rating),
                icon = Icons.Default.Info,
                modifier = cardModifier
            )
        })

        Spacer(modifier = Modifier.height(12.dp))

        AdaptivePair(first = { cardModifier ->
            StatCard(
                title = stringResource(R.string.progress_current_streak),
                value = stringResource(R.string.progress_streak_days, stats.currentStreak),
                subtitle = stringResource(R.string.progress_streak_keep_up),
                icon = Icons.Default.DateRange,
                modifier = cardModifier
            )
        }, second = { cardModifier ->
            StatCard(
                title = stringResource(R.string.progress_best_session),
                value = "${(stats.bestSessionScore * 100).toInt()}%",
                subtitle = stringResource(R.string.progress_highest_score),
                icon = Icons.Default.Star,
                modifier = cardModifier
            )
        })

        Spacer(modifier = Modifier.height(16.dp))
        // The recent-sessions preview was removed from Overview; the full list still
        // lives in the History tab.
    }
}

@Composable
private fun DailyChartTab(state: ProgressUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.progress_chart_recitations_per_day),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LotusCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                DailyBarChart(data = state.dailyCounts)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AccuracyTab(state: ProgressUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.progress_chart_accuracy_trend),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LotusCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                AccuracyLineChart(data = state.accuracyTrend)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Per-axis trends: correctness / completeness / pronunciation (Phase 4.4).
        Text(
            text = stringResource(R.string.progress_axis_trends_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LotusCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                AxisTrendChart(data = state.axisTrends)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HistoryTab(state: ProgressUiState.Success, onMore: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.progress_history_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SessionHistoryList(
            sessions = state.sessions,
            modifier = Modifier.weight(1f),
            footer = {
                if (state.appending) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.hasMore) androidx.compose.material3.OutlinedButton(onClick = onMore,
                    enabled = !state.appending, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.progress_load_more))
                }
            }
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    LotusCard(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
