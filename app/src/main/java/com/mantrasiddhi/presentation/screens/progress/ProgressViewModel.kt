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
import com.mantrasiddhi.domain.usecase.DailyCount
import com.mantrasiddhi.domain.usecase.GetAccuracyTrendUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.GetDailyRecitationCountsUseCase
import com.mantrasiddhi.domain.usecase.GetRecitationStatsUseCase
import com.mantrasiddhi.domain.usecase.GetSessionsForUserUseCase
import com.mantrasiddhi.domain.usecase.GetWordMasteryUseCase
import com.mantrasiddhi.domain.usecase.RecitationOverviewStats
import com.mantrasiddhi.domain.usecase.WordMastery
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
        val wordMastery: List<WordMastery>,
        val sessions: List<RecitationSession>
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
    private val getWordMasteryUseCase: GetWordMasteryUseCase,
    private val getSessionsForUserUseCase: GetSessionsForUserUseCase
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgressUiState> = getCurrentUserUseCase()
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(ProgressUiState.Loading)
            } else {
                combine(
                    getRecitationStatsUseCase(user.id),
                    getDailyRecitationCountsUseCase(user.id),
                    getAccuracyTrendUseCase(user.id),
                    getWordMasteryUseCase(user.id),
                    getSessionsForUserUseCase(user.id)
                ) { stats, daily, accuracy, mastery, sessions ->
                    ProgressUiState.Success(
                        overviewStats = stats,
                        dailyCounts = daily,
                        accuracyTrend = accuracy,
                        wordMastery = mastery,
                        sessions = sessions
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProgressUiState.Loading
        )
}
