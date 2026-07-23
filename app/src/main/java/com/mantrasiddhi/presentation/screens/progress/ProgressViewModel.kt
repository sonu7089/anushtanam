/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.domain.model.RecitationSession
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
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getRecitationStatsUseCase: GetRecitationStatsUseCase,
    private val getDailyRecitationCountsUseCase: GetDailyRecitationCountsUseCase,
    private val getAccuracyTrendUseCase: GetAccuracyTrendUseCase,
    private val getSessionsForUserUseCase: GetSessionsForUserUseCase,
    private val getAxisTrendsUseCase: GetAxisTrendsUseCase
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
        getAccuracyTrendUseCase(userId)
    ) { stats, daily, accuracy -> CoreStats(stats, daily, accuracy) }

    private fun combineTrends(userId: Long) = combine(
        getSessionsForUserUseCase(userId),
        getAxisTrendsUseCase(userId)
    ) { sessions, axisTrends -> TrendStats(sessions, axisTrends) }
}
