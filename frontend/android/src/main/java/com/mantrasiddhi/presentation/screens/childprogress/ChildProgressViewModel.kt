/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.childprogress

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.data.notification.GoalNotifier
import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.domain.model.RewardType
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
    /** @property messageResId copy to show; the ViewModel never builds display text itself. */
    data class Error(@StringRes val messageResId: Int) : ChildProgressUiState
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
    /** @property messageResId copy to show; the ViewModel never builds display text itself. */
    data class Error(@StringRes val messageResId: Int) : ChildDetailUiState
}

sealed class ChildProgressEvent {
    /**
     * @param messageResId the parent-facing sentence.
     * @param detail a throwable's own message when one exists. It is not localizable, so the
     *   screen prefers it only because it is more specific than the generic fallback.
     */
    data class ShowError(
        @StringRes val messageResId: Int,
        val detail: String? = null
    ) : ChildProgressEvent()
    data object TargetSetSuccess : ChildProgressEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChildProgressViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getChildrenUseCase: GetChildrenUseCase,
    private val getChildProgressUseCase: GetChildProgressUseCase,
    private val getChildWeakWordsUseCase: GetChildWeakWordsUseCase,
    private val createTargetUseCase: CreateTargetUseCase,
    private val goalNotifier: GoalNotifier
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
                        flowOf(ChildProgressUiState.Error(R.string.child_error_parent_only))
                    }
                } else {
                    flowOf(ChildProgressUiState.Error(R.string.child_error_no_parent_profile))
                }
            }.catch {
                emit(ChildProgressUiState.Error(R.string.child_error_load_children))
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
                    ChildDetailUiState.Error(R.string.child_error_not_found)
                }
            }.catch {
                emit(ChildDetailUiState.Error(R.string.child_error_load_details))
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
     * Sets a target for a child from the parent dashboard. Since this is always a
     * parent-for-child goal, a local celebratory notification is posted on success.
     */
    fun setChildTarget(childId: Long, count: Int, rewardType: RewardType, rewardDescription: String) {
        viewModelScope.launch {
            val parentProfile = (uiState.value as? ChildProgressUiState.Success)?.parentUser ?: return@launch
            val target = Target(
                userId = childId,
                setByUserId = parentProfile.id,
                targetRecitationCount = count,
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
                _events.emit(ChildProgressEvent.TargetSetSuccess)
            }.onFailure { e ->
                _events.emit(
                    ChildProgressEvent.ShowError(
                        messageResId = R.string.child_error_set_target,
                        detail = e.message
                    )
                )
            }
        }
    }
}
