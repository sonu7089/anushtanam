/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.childprogress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.CreateTargetUseCase
import com.mantrasiddhi.domain.usecase.GetChildProgressUseCase
import com.mantrasiddhi.domain.usecase.GetChildWeakWordsUseCase
import com.mantrasiddhi.domain.usecase.GetChildrenUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State representing children list progress on the main dashboard.
 */
sealed interface ChildProgressUiState {
    data object Loading : ChildProgressUiState
    data class Success(
        val parentUser: UserProfile,
        val childrenProgress: List<ChildProgress>
    ) : ChildProgressUiState
    data class Error(val message: String) : ChildProgressUiState
}

/**
 * UI State representing child profile details (drill down screen).
 */
sealed interface ChildDetailUiState {
    data object Idle : ChildDetailUiState
    data object Loading : ChildDetailUiState
    data class Success(
        val childProgress: ChildProgress,
        val weakWords: List<String>
    ) : ChildDetailUiState
    data class Error(val message: String) : ChildDetailUiState
}

sealed class ChildProgressEvent {
    data class ShowError(val message: String) : ChildProgressEvent()
    data object TargetSetSuccess : ChildProgressEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChildProgressViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getChildrenUseCase: GetChildrenUseCase,
    private val getChildProgressUseCase: GetChildProgressUseCase,
    private val getChildWeakWordsUseCase: GetChildWeakWordsUseCase,
    private val createTargetUseCase: CreateTargetUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChildProgressUiState>(ChildProgressUiState.Loading)
    val uiState: StateFlow<ChildProgressUiState> = _uiState.asStateFlow()

    private val _detailUiState = MutableStateFlow<ChildDetailUiState>(ChildDetailUiState.Idle)
    val detailUiState: StateFlow<ChildDetailUiState> = _detailUiState.asStateFlow()

    private val _events = MutableSharedFlow<ChildProgressEvent>()
    val events: SharedFlow<ChildProgressEvent> = _events.asSharedFlow()

    init {
        observeChildrenProgress()
    }

    private fun observeChildrenProgress() {
        viewModelScope.launch {
            getCurrentUserUseCase().flatMapLatest { user ->
                if (user != null) {
                    if (user.profileType == com.mantrasiddhi.domain.model.ProfileType.PARENT) {
                        getChildrenUseCase(user.id).flatMapLatest { childrenList ->
                            if (childrenList.isEmpty()) {
                                flowOf(ChildProgressUiState.Success(user, emptyList()))
                            } else {
                                val progressFlows = childrenList.map { getChildProgressUseCase(it.id) }
                                combine(progressFlows) { progresses ->
                                    ChildProgressUiState.Success(user, progresses.filterNotNull().toList())
                                }
                            }
                        }
                    } else {
                        flowOf(ChildProgressUiState.Error("Access Denied: Only parent profiles can view child progress."))
                    }
                } else {
                    flowOf(ChildProgressUiState.Error("No active parent profile selected."))
                }
            }.catch { e ->
                emit(ChildProgressUiState.Error(e.message ?: "Failed to retrieve children progress."))
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Loads detailed progress and weak words for a specific child.
     */
    fun selectChild(childId: Long) {
        _detailUiState.value = ChildDetailUiState.Loading
        viewModelScope.launch {
            combine(
                getChildProgressUseCase(childId),
                getChildWeakWordsUseCase(childId)
            ) { progress, weakWords ->
                if (progress != null) {
                    ChildDetailUiState.Success(progress, weakWords)
                } else {
                    ChildDetailUiState.Error("Child profile not found.")
                }
            }.catch { e ->
                emit(ChildDetailUiState.Error(e.message ?: "Failed to load child details."))
            }.collect { state ->
                _detailUiState.value = state
            }
        }
    }

    /**
     * Resets active child detail view.
     */
    fun clearSelectedChild() {
        _detailUiState.value = ChildDetailUiState.Idle
    }

    /**
     * Sets a target for a child from parent dashboard.
     */
    fun setChildTarget(childId: Long, count: Int, moneyAmount: Double) {
        viewModelScope.launch {
            val parentProfile = (uiState.value as? ChildProgressUiState.Success)?.parentUser ?: return@launch
            val target = Target(
                userId = childId,
                setByUserId = parentProfile.id,
                targetRecitationCount = count,
                currentRecitationCount = 0,
                moneyAmount = moneyAmount,
                isCompleted = false
            )
            createTargetUseCase(target).onSuccess {
                _events.emit(ChildProgressEvent.TargetSetSuccess)
            }.onFailure { e ->
                _events.emit(ChildProgressEvent.ShowError(e.message ?: "Failed to set child target."))
            }
        }
    }
}
