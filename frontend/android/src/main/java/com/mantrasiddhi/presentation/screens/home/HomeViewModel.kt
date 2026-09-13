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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onEach
import com.mantrasiddhi.presentation.state.profileStateWhileSubscribed
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getDashboardStatsUseCase: GetDashboardStatsUseCase,
    private val getActivePurashcharanaUseCase: GetActivePurashcharanaUseCase,
    private val getJapaSummary: com.mantrasiddhi.domain.usecase.GetJapaSummaryUseCase,
    private val teachings: com.mantrasiddhi.data.messages.TeachingRepository,
    private val calendarTime: com.mantrasiddhi.domain.util.CalendarTime = com.mantrasiddhi.domain.util.CalendarTime()
) : ViewModel() {

    val reflection = teachings.reflection
    private var reflectionJob: kotlinx.coroutines.Job? = null

    fun refreshReflection() {
        if (reflectionJob?.isActive == true) return
        reflectionJob = viewModelScope.launch {
            try { teachings.refreshReflection() }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { /* The built-in notes keep Home useful. */ }
        }
    }

    private val retryRequest = MutableStateFlow(0)
    fun retry() { retryRequest.value++ }
    fun stopReflection() { reflectionJob?.cancel(); reflectionJob = null }

    private data class Section<T>(val value: T? = null, val loading: Boolean = true, val failed: Boolean = false)
    private var cachedStats = Section<com.mantrasiddhi.domain.usecase.DashboardStats?>()
    private var cachedVow = Section<com.mantrasiddhi.domain.model.Purashcharana?>()
    private var cachedJapa = Section<com.mantrasiddhi.domain.usecase.JapaSummary>()
    private fun <T> kotlinx.coroutines.flow.Flow<T>.section(initial: Section<T>, save: (Section<T>) -> Unit): kotlinx.coroutines.flow.Flow<Section<T>> =
        map { Section(value = it, loading = false) }.onStart { emit(initial) }
            .catch { emit(Section(loading = false, failed = true)) }.onEach(save)

    val uiState: StateFlow<HomeUiState> = getCurrentUserUseCase().profileStateWhileSubscribed(
        viewModelScope, HomeUiState.Loading,
        reset = { user ->
            cachedStats = Section(); cachedVow = Section(); cachedJapa = Section()
            if (user == null) HomeUiState.Error(R.string.home_error_no_profile) else HomeUiState.Loading
        }
    ) { user ->
            retryRequest.flatMapLatest {
                combine(getDashboardStatsUseCase(user.id).section(cachedStats) { cachedStats = it },
                    getActivePurashcharanaUseCase(user.id).section(cachedVow) { cachedVow = it },
                    getJapaSummary(user.id).section(cachedJapa) { cachedJapa = it }, calendarTime.days()) { stats, vow, japa, calendar ->
                    HomeUiState.Success(user = user, todayCount = stats.value?.todayRecitationCount ?: 0,
                        todayJapa = japa.value?.on(calendar.first)?.toString(),
                        activeTarget = stats.value?.activeTarget, streakDays = stats.value?.streakDays ?: 0,
                        purashcharana = vow.value, statsLoading = stats.loading,
                        statsFailed = stats.failed || (!stats.loading && stats.value == null),
                        japaLoading = japa.loading, japaFailed = japa.failed,
                        vowLoading = vow.loading, vowFailed = vow.failed)
                }
            }
    }
}
