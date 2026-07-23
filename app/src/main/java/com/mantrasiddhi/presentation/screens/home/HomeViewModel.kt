/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.domain.usecase.GetDashboardStatsUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getDashboardStatsUseCase: GetDashboardStatsUseCase
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
                    getDashboardStatsUseCase(user.id).catch { e ->
                        emit(null)
                    }.map { stats ->
                        if (stats != null) {
                            HomeUiState.Success(
                                user = stats.user,
                                todayCount = stats.todayRecitationCount,
                                activeTarget = stats.activeTarget,
                                streakDays = stats.streakDays,
                                childrenProgress = stats.childrenProgress
                            )
                        } else {
                            HomeUiState.Error("Failed to load dashboard statistics.")
                        }
                    }
                } else {
                    flowOf(HomeUiState.Error("No active profile selected."))
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
