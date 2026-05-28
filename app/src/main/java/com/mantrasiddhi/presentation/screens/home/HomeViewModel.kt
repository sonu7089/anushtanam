/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.GetActiveTargetUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.GetSessionsForUserUseCase
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
import java.util.Calendar
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getActiveTargetUseCase: GetActiveTargetUseCase,
    private val getSessionsForUserUseCase: GetSessionsForUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeUserAndData()
    }

    private fun observeUserAndData() {
        viewModelScope.launch {
            getCurrentUserUseCase().flatMapLatest { user ->
                if (user != null) {
                    // Fetch target on CPU/IO first
                    val activeTarget = getActiveTargetUseCase(user.id)
                    // Flow recitation sessions
                    getSessionsForUserUseCase(user.id).catch { e ->
                        emit(emptyList())
                    }.map { sessions ->
                        val todayCount = sessions.filter { isToday(it.createdAt) && it.isCorrect }.size
                        HomeUiState.Success(user, todayCount, activeTarget)
                    }
                } else {
                    flowOf(HomeUiState.Error("No active user profile found."))
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    }
}
