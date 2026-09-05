/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.domain.model.ProgressSummary
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.ProgressSyncRepository
import com.mantrasiddhi.domain.usecase.DailyAccuracy
import com.mantrasiddhi.domain.usecase.DailyAxisScores
import com.mantrasiddhi.domain.usecase.DailyCount
import com.mantrasiddhi.domain.usecase.GetAccuracyTrendUseCase
import com.mantrasiddhi.domain.usecase.GetAxisTrendsUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.GetDailyRecitationCountsUseCase
import com.mantrasiddhi.domain.usecase.GetRecitationStatsUseCase
import com.mantrasiddhi.domain.usecase.GetSessionsForUserUseCase
import com.mantrasiddhi.domain.usecase.RecitationOverviewStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * UI State for the Progress Screen.
 */
sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data class Success(
        val overviewStats: RecitationOverviewStats,
        val dailyCounts: List<DailyCount>,
        val accuracyTrend: List<DailyAccuracy>,
        val sessions: List<RecitationSession>,
        val axisTrends: List<DailyAxisScores>
    ) : ProgressUiState
    data class Error(val message: String) : ProgressUiState
}

/**
 * ViewModel to manage analytics and progress stats.
 *
 * Overview stats and the daily/accuracy charts prefer the cloud [ProgressSummary]
 * (so they survive reinstall / device switch) and fall back to the local Room
 * aggregates when no cloud summary exists. Session history and axis-trends are
 * device-local by design and always come from Room.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getRecitationStatsUseCase: GetRecitationStatsUseCase,
    private val getDailyRecitationCountsUseCase: GetDailyRecitationCountsUseCase,
    private val getAccuracyTrendUseCase: GetAccuracyTrendUseCase,
    private val getSessionsForUserUseCase: GetSessionsForUserUseCase,
    private val getAxisTrendsUseCase: GetAxisTrendsUseCase,
    private val progressSyncRepository: ProgressSyncRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgressUiState> = getCurrentUserUseCase()
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(ProgressUiState.Loading)
            } else {
                combine(
                    combineCore(user.id),
                    combineTrends(user.id)
                ) { core, trends ->
                    ProgressUiState.Success(
                        overviewStats = core.stats,
                        dailyCounts = core.dailyCounts,
                        accuracyTrend = core.accuracyTrend,
                        sessions = trends.sessions,
                        axisTrends = trends.axisTrends
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProgressUiState.Loading
        )

    private data class CoreStats(
        val stats: RecitationOverviewStats,
        val dailyCounts: List<DailyCount>,
        val accuracyTrend: List<DailyAccuracy>
    )

    private data class TrendStats(
        val sessions: List<RecitationSession>,
        val axisTrends: List<DailyAxisScores>
    )

    private fun combineCore(userId: Long) = combine(
        getRecitationStatsUseCase(userId),
        getDailyRecitationCountsUseCase(userId),
        getAccuracyTrendUseCase(userId),
        progressSyncRepository.summaryFlow()
    ) { stats, daily, accuracy, summary ->
        if (summary == null) {
            // No cloud summary (signed out / doc not created yet): local values as-is.
            CoreStats(stats, daily, accuracy)
        } else {
            val todayKey = LocalDate.now().toString()
            CoreStats(
                stats = summary.toOverviewStats(todayKey),
                dailyCounts = summary.toDailyCounts(todayKey),
                accuracyTrend = summary.toAccuracyTrend(todayKey)
            )
        }
    }

    private fun combineTrends(userId: Long) = combine(
        getSessionsForUserUseCase(userId),
        getAxisTrendsUseCase(userId)
    ) { sessions, axisTrends -> TrendStats(sessions, axisTrends) }

    private fun ProgressSummary.toOverviewStats(todayKey: String) = RecitationOverviewStats(
        totalSessions = totalSessions.toInt(),
        totalCorrectRecitations = correctSessions.toInt(),
        averageAccuracy = averageAccuracy,
        currentStreak = currentStreak(todayKey),
        bestSessionScore = bestScore
    )

    private fun ProgressSummary.toDailyCounts(todayKey: String): List<DailyCount> =
        dailyCounts(CHART_WINDOW_DAYS, todayKey).map { (dateKey, count) ->
            val millis = dateKeyToMillis(dateKey)
            DailyCount(dayLabel = dayLabel(millis), dateMillis = millis, count = count)
        }

    private fun ProgressSummary.toAccuracyTrend(todayKey: String): List<DailyAccuracy> =
        accuracyTrend(CHART_WINDOW_DAYS, todayKey).map { (dateKey, average) ->
            val millis = dateKeyToMillis(dateKey)
            DailyAccuracy(dayLabel = dayLabel(millis), dateMillis = millis, averageScore = average)
        }

    private fun dateKeyToMillis(dateKey: String): Long =
        LocalDate.parse(dateKey).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Same weekday label the local 7-day aggregates use ("EEE"). */
    private fun dayLabel(dateMillis: Long): String =
        SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dateMillis))

    private companion object {
        /** The charts show the last 7 days, matching the local use-case defaults. */
        const val CHART_WINDOW_DAYS = 7
    }
}
