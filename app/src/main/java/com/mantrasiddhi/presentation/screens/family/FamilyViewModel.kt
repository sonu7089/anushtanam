/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.model.ParentChildLink
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.usecase.ApproveLinkUseCase
import com.mantrasiddhi.domain.usecase.CreateChildGoalUseCase
import com.mantrasiddhi.domain.usecase.ObserveAuthAccountUseCase
import com.mantrasiddhi.domain.usecase.ObserveChildGoalsUseCase
import com.mantrasiddhi.domain.usecase.ObserveGoalsForChildUseCase
import com.mantrasiddhi.domain.usecase.ObserveLinkedChildrenUseCase
import com.mantrasiddhi.domain.usecase.ObservePendingLinkRequestsUseCase
import com.mantrasiddhi.domain.usecase.RejectLinkUseCase
import com.mantrasiddhi.domain.usecase.RequestChildLinkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Family screen: parent linking + goal-setting, child link-approval + goals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FamilyViewModel @Inject constructor(
    observeAuthAccountUseCase: ObserveAuthAccountUseCase,
    private val requestChildLinkUseCase: RequestChildLinkUseCase,
    private val approveLinkUseCase: ApproveLinkUseCase,
    private val rejectLinkUseCase: RejectLinkUseCase,
    private val createChildGoalUseCase: CreateChildGoalUseCase,
    observeLinkedChildrenUseCase: ObserveLinkedChildrenUseCase,
    observePendingLinkRequestsUseCase: ObservePendingLinkRequestsUseCase,
    observeChildGoalsUseCase: ObserveChildGoalsUseCase,
    private val observeGoalsForChildUseCase: ObserveGoalsForChildUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilyUiState())
    val uiState: StateFlow<FamilyUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val selectedChildUid = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            observeAuthAccountUseCase().collect { account ->
                _uiState.update { it.copy(account = account) }
            }
        }
        viewModelScope.launch {
            observeAuthAccountUseCase().flatMapLatest { acc ->
                if (acc?.role == AccountRole.PARENT) observeLinkedChildrenUseCase() else flowOf(emptyList())
            }.collect { children -> _uiState.update { it.copy(linkedChildren = children) } }
        }
        viewModelScope.launch {
            observeAuthAccountUseCase().flatMapLatest { acc ->
                if (acc?.role == AccountRole.CHILD) observePendingLinkRequestsUseCase() else flowOf(emptyList())
            }.collect { reqs -> _uiState.update { it.copy(pendingRequests = reqs) } }
        }
        viewModelScope.launch {
            observeAuthAccountUseCase().flatMapLatest { acc ->
                if (acc?.role == AccountRole.CHILD) observeChildGoalsUseCase() else flowOf(emptyList())
            }.collect { goals -> _uiState.update { it.copy(myGoals = goals) } }
        }
        viewModelScope.launch {
            selectedChildUid.flatMapLatest { uid ->
                if (uid != null) observeGoalsForChildUseCase(uid) else flowOf(emptyList())
            }.collect { goals -> _uiState.update { it.copy(selectedChildGoals = goals) } }
        }
    }

    fun requestLink(childCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            requestChildLinkUseCase(childCode)
                .onSuccess { _messages.emit(MSG_LINK_REQUEST_SENT) }
                .onFailure { e -> _messages.emit(e.message ?: MSG_GENERIC_ERROR) }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun approve(linkId: String) {
        viewModelScope.launch {
            approveLinkUseCase(linkId)
                .onSuccess { _messages.emit(MSG_LINK_APPROVED) }
                .onFailure { e -> _messages.emit(e.message ?: MSG_GENERIC_ERROR) }
        }
    }

    fun reject(linkId: String) {
        viewModelScope.launch {
            rejectLinkUseCase(linkId)
                .onFailure { e -> _messages.emit(e.message ?: MSG_GENERIC_ERROR) }
        }
    }

    fun selectChild(link: ParentChildLink?) {
        _uiState.update { it.copy(selectedChild = link, selectedChildGoals = emptyList()) }
        selectedChildUid.value = link?.childUid
    }

    fun createGoal(childUid: String, rewardType: RewardType, rewardDescription: String, targetCount: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            createChildGoalUseCase(childUid, rewardType, rewardDescription, targetCount)
                .onSuccess { _messages.emit(MSG_GOAL_CREATED) }
                .onFailure { e -> _messages.emit(e.message ?: MSG_GENERIC_ERROR) }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private companion object {
        // Marker keys resolved to localized strings by the screen.
        const val MSG_LINK_REQUEST_SENT = "link_request_sent"
        const val MSG_LINK_APPROVED = "link_approved"
        const val MSG_GOAL_CREATED = "goal_created"
        const val MSG_GENERIC_ERROR = "generic_error"
    }
}
