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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
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
    private val getJapaSummary: com.mantrasiddhi.domain.usecase.GetJapaSummaryUseCase,
    private val teachings: com.mantrasiddhi.data.messages.TeachingRepository
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
    private fun <T> kotlinx.coroutines.flow.Flow<T>.section(): kotlinx.coroutines.flow.Flow<Section<T>> =
        map { Section(value = it, loading = false) }.onStart { emit(Section()) }
            .catch { emit(Section(loading = false, failed = true)) }

    val uiState: StateFlow<HomeUiState> = getCurrentUserUseCase().flatMapLatest { user ->
        if (user == null) flowOf(HomeUiState.Error(R.string.home_error_no_profile)) else
            retryRequest.flatMapLatest {
                combine(getDashboardStatsUseCase(user.id).section(),
                    getActivePurashcharanaUseCase(user.id).section(), getJapaSummary(user.id).section()) { stats, vow, japa ->
                    HomeUiState.Success(user = user, todayCount = stats.value?.todayRecitationCount ?: 0,
                        todayJapa = japa.value?.on(java.time.LocalDate.now())?.toString(),
                        activeTarget = stats.value?.activeTarget, streakDays = stats.value?.streakDays ?: 0,
                        purashcharana = vow.value, statsLoading = stats.loading,
                        statsFailed = stats.failed || (!stats.loading && stats.value == null),
                        japaLoading = japa.loading, japaFailed = japa.failed,
                        vowLoading = vow.loading, vowFailed = vow.failed)
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), HomeUiState.Loading)
}
