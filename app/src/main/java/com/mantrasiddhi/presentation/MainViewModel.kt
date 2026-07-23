/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.notification.GoalNotifier
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.ObserveAuthAccountUseCase
import com.mantrasiddhi.domain.usecase.ObserveChildGoalsUseCase
import com.mantrasiddhi.domain.usecase.SyncSignedInUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-entry auth decision. */
sealed interface AuthGateState {
    data object Loading : AuthGateState
    data object Authenticated : AuthGateState
    data object Unauthenticated : AuthGateState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    getCurrentUserUseCase: GetCurrentUserUseCase,
    private val preferencesDataStore: UserPreferencesDataStore,
    observeAuthAccountUseCase: ObserveAuthAccountUseCase,
    observeChildGoalsUseCase: ObserveChildGoalsUseCase,
    private val syncSignedInUserUseCase: SyncSignedInUserUseCase,
    private val goalNotifier: GoalNotifier
) : ViewModel() {
    val currentUser = getCurrentUserUseCase()

    val themeMode: StateFlow<String> = preferencesDataStore.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    private val _authGateState = MutableStateFlow<AuthGateState>(AuthGateState.Loading)
    val authGateState: StateFlow<AuthGateState> = _authGateState.asStateFlow()

    init {
        // Decide the entry route and bridge the cloud account to a local profile.
        viewModelScope.launch {
            observeAuthAccountUseCase()
                .distinctUntilChangedBy { it?.uid }
                .collect { account ->
                    if (account == null) {
                        _authGateState.value = AuthGateState.Unauthenticated
                    } else {
                        runCatching { syncSignedInUserUseCase(account) }
                        _authGateState.value = AuthGateState.Authenticated
                    }
                }
        }

        // No-Blaze notification MVP: while a CHILD is signed in and the app is in the
        // foreground, surface newly-created goals as local notifications. The first
        // emission for each signed-in child establishes a baseline of existing goal ids
        // (so we never re-notify pre-existing goals); only goals appearing afterwards
        // notify. The per-account baseline resets whenever the account changes.
        viewModelScope.launch {
            observeAuthAccountUseCase()
                .flatMapLatest { account ->
                    if (account?.role != AccountRole.CHILD) {
                        flowOf(emptyList())
                    } else {
                        var baseline: Set<String>? = null
                        observeChildGoalsUseCase().map { goals ->
                            val prev = baseline
                            if (prev == null) {
                                baseline = goals.map { it.id }.toSet()
                                emptyList()
                            } else {
                                val fresh = goals.filter { it.id !in prev }
                                baseline = prev + fresh.map { it.id }
                                fresh
                            }
                        }
                    }
                }
                .collect { freshGoals ->
                    freshGoals.forEach { goal ->
                        goalNotifier.showGoalSet(
                            count = goal.targetRecitationCount,
                            rewardType = goal.rewardType,
                            rewardDescription = goal.rewardDescription
                        )
                    }
                }
        }
    }
}
