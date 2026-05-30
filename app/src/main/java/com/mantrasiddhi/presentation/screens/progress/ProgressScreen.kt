/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.progress

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.R
import com.mantrasiddhi.presentation.components.EmptyState
import com.mantrasiddhi.presentation.components.SkeletonCardItem
import com.mantrasiddhi.presentation.components.SkeletonSessionItem
import com.mantrasiddhi.presentation.screens.progress.components.AccuracyLineChart
import com.mantrasiddhi.presentation.screens.progress.components.AxisTrendChart
import com.mantrasiddhi.presentation.screens.progress.components.DailyBarChart
import com.mantrasiddhi.presentation.screens.progress.components.FocusDrillCard
import com.mantrasiddhi.presentation.screens.progress.components.SessionHistoryList
import com.mantrasiddhi.presentation.screens.progress.components.WordMasteryGrid

/**
 * Main Progress and Analytics Screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.title_progress),
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
            Column(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is ProgressUiState.Loading -> {
                        ProgressLoadingSkeleton()
                    }
                    is ProgressUiState.Error -> {
                        ProgressErrorScreen(message = state.message)
                    }
                    is ProgressUiState.Success -> {
                        ProgressContent(state = state)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
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
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProgressContent(state: ProgressUiState.Success) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        R.string.progress_tab_overview,
        R.string.progress_tab_daily_chart,
        R.string.progress_tab_accuracy,
        R.string.progress_tab_word_mastery,
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
                onClick = { selectedTab = index },
                text = { Text(text = stringResource(titleRes), fontWeight = FontWeight.SemiBold) }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (state.sessions.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.progress_empty_title),
                description = stringResource(R.string.progress_empty_description)
            )
        } else {
            when (selectedTab) {
                0 -> OverviewTab(state = state)
                1 -> DailyChartTab(state = state)
                2 -> AccuracyTab(state = state)
                3 -> WordMasteryTab(state = state)
                4 -> HistoryTab(state = state)
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

        Text(
            text = stringResource(R.string.progress_activity_summary),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            StatCard(
                title = stringResource(R.string.progress_total_recitations),
                value = stats.totalCorrectRecitations.toString(),
                subtitle = stringResource(R.string.progress_out_of_attempts, stats.totalSessions),
                icon = Icons.Default.Star,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            StatCard(
                title = stringResource(R.string.progress_average_accuracy),
                value = "${(stats.averageAccuracy * 100).toInt()}%",
                subtitle = stringResource(R.string.progress_overall_rating),
                icon = Icons.Default.Info,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatCard(
                title = stringResource(R.string.progress_current_streak),
                value = stringResource(R.string.progress_streak_days, stats.currentStreak),
                subtitle = stringResource(R.string.progress_streak_keep_up),
                icon = Icons.Default.DateRange,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            StatCard(
                title = stringResource(R.string.progress_best_session),
                value = "${(stats.bestSessionScore * 100).toInt()}%",
                subtitle = stringResource(R.string.progress_highest_score),
                icon = Icons.Default.Star,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent session preview
        Text(
            text = stringResource(R.string.progress_recent_sessions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SessionHistoryList(
            sessions = state.sessions.take(3),
            modifier = Modifier.fillMaxWidth(),
            isScrollable = false
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Focused, spaced-repetition practice plan (Phase 4.4).
        FocusDrillCard(plan = state.practicePlan)

        Spacer(modifier = Modifier.height(16.dp))
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
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AccuracyLineChart(data = state.accuracyTrend)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Per-axis trends: correctness / completeness / pronunciation (Phase 4.4).
        Text(
            text = stringResource(R.string.progress_axis_trends_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AxisTrendChart(data = state.axisTrends)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WordMasteryTab(state: ProgressUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.progress_word_mastery_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.progress_word_mastery_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                WordMasteryGrid(wordMasteryList = state.wordMastery)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HistoryTab(state: ProgressUiState.Success) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.progress_history_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SessionHistoryList(
            sessions = state.sessions,
            modifier = Modifier.weight(1f)
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}
