/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.targets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.notification.GoalNotifier
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.usecase.AbandonTargetUseCase
import com.mantrasiddhi.domain.usecase.CreateTargetUseCase
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
 * UI State for the Targets screen.
 */
sealed interface TargetsUiState {
    data object Loading : TargetsUiState
    data class Success(
        val currentUser: UserProfile,
        val activeTarget: Target?,
        val completedTargets: List<Target>,
        val children: List<UserProfile> = emptyList()
    ) : TargetsUiState
    data class Error(val message: String) : TargetsUiState
}

/**
 * One-shot UI Events from Targets screen.
 */
sealed class TargetsEvent {
    data class ShowError(val message: String) : TargetsEvent()
    data object TargetCreated : TargetsEvent()
    data object TargetAbandoned : TargetsEvent()
}

/**
 * ViewModel for the Targets screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TargetsViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val createTargetUseCase: CreateTargetUseCase,
    private val abandonTargetUseCase: AbandonTargetUseCase,
    private val getChildrenUseCase: GetChildrenUseCase,
    private val targetRepository: TargetRepository,
    private val goalNotifier: GoalNotifier
) : ViewModel() {

    private val _uiState = MutableStateFlow<TargetsUiState>(TargetsUiState.Loading)
    val uiState: StateFlow<TargetsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TargetsEvent>()
    val events: SharedFlow<TargetsEvent> = _events.asSharedFlow()

    init {
        observeUserAndTargets()
    }

    private fun observeUserAndTargets() {
        viewModelScope.launch {
            getCurrentUserUseCase().flatMapLatest { user ->
                if (user != null) {
                    val targetsFlow = targetRepository.getAllForUser(user.id)
                    val childrenFlow = if (user.profileType == com.mantrasiddhi.domain.model.ProfileType.PARENT) {
                        getChildrenUseCase(user.id)
                    } else {
                        flowOf(emptyList())
                    }
                    combine(targetsFlow, childrenFlow) { targets, children ->
                        val active = targets.firstOrNull { !it.isCompleted }
                        val completed = targets.filter { it.isCompleted }
                        TargetsUiState.Success(
                            currentUser = user,
                            activeTarget = active,
                            completedTargets = completed,
                            children = children
                        )
                    }
                } else {
                    flowOf(TargetsUiState.Error("No active user profile selected."))
                }
            }.catch { e ->
                emit(TargetsUiState.Error(e.message ?: "Failed to load targets data."))
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Creates a new target for the specified user ID (or current user if null).
     *
     * When a parent sets a goal FOR a child (the target user differs from the
     * profile that set it), a local celebratory notification is posted.
     */
    fun createTarget(
        targetRecitationCount: Int,
        rewardType: RewardType,
        rewardDescription: String,
        targetUserId: Long? = null
    ) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is TargetsUiState.Success) {
                val userId = targetUserId ?: currentState.currentUser.id
                val target = Target(
                    userId = userId,
                    setByUserId = currentState.currentUser.id,
                    targetRecitationCount = targetRecitationCount,
                    currentRecitationCount = 0,
                    rewardType = rewardType,
                    rewardDescription = rewardDescription,
                    isCompleted = false
                )
                createTargetUseCase(target).onSuccess {
                    if (target.setByUserId != target.userId) {
                        goalNotifier.showGoalSet(
                            count = target.targetRecitationCount,
                            rewardType = target.rewardType,
                            rewardDescription = target.rewardDescription
                        )
                    }
                    _events.emit(TargetsEvent.TargetCreated)
                }.onFailure { e ->
                    _events.emit(TargetsEvent.ShowError(e.message ?: "Failed to set target."))
                }
            } else {
                _events.emit(TargetsEvent.ShowError("State is not ready to create target."))
            }
        }
    }

    /**
     * Abandons the active target for the specified user ID (or current user if null).
     */
    fun abandonTarget(targetUserId: Long? = null) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is TargetsUiState.Success) {
                val userId = targetUserId ?: currentState.currentUser.id
                val succeeded = abandonTargetUseCase(userId)
                if (succeeded) {
                    _events.emit(TargetsEvent.TargetAbandoned)
                } else {
                    _events.emit(TargetsEvent.ShowError("No active target found to abandon."))
                }
            }
        }
    }
}
