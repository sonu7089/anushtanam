/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.usecase.GetActivePurashcharanaUseCase
import com.mantrasiddhi.domain.usecase.GetDashboardStatsUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getDashboardStatsUseCase: GetDashboardStatsUseCase,
    private val getActivePurashcharanaUseCase: GetActivePurashcharanaUseCase,
    private val getJapaSummary: com.mantrasiddhi.domain.usecase.GetJapaSummaryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeDashboardStats()
    }

    private fun observeDashboardStats() {
        viewModelScope.launch {
            getCurrentUserUseCase().flatMapLatest { user ->
                if (user != null) {
                    combine(
                        getDashboardStatsUseCase(user.id).catch { emit(null) },
                        // The Purashcharana is one card on a dashboard, so it may neither
                        // break the page nor hold it up: `catch` degrades a failure to null,
                        // and `onStart` seeds a null so `combine` can emit as soon as the
                        // stats arrive instead of waiting on the sankalpa query.
                        getActivePurashcharanaUseCase(user.id)
                            .catch { emit(null) }
                            .onStart { emit(null) },
                        getJapaSummary(user.id)
                    ) { stats, purashcharana, japa ->
                        if (stats != null) {
                            HomeUiState.Success(
                                user = stats.user,
                                todayCount = stats.todayRecitationCount,
                                todayJapa = japa.on(java.time.LocalDate.now()).toString(),
                                activeTarget = stats.activeTarget,
                                streakDays = stats.streakDays,
                                childrenProgress = stats.childrenProgress,
                                purashcharana = purashcharana
                            )
                        } else {
                            HomeUiState.Error(R.string.home_error_load_failed)
                        }
                    }
                } else {
                    flowOf(HomeUiState.Error(R.string.home_error_no_profile))
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
