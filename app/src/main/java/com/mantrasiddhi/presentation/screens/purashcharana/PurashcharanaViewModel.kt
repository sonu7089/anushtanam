/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.purashcharana

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.domain.usecase.EnrollPurashcharanaUseCase
import com.mantrasiddhi.domain.usecase.GetActivePurashcharanaUseCase
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.WithdrawPurashcharanaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Purashcharana screen.
 *
 * The sankalpa is never mutated from here except by the two deliberate acts a sadhak can
 * perform — taking it and setting it aside. The *count* deliberately has no entry point at
 * all: it moves only through [com.mantrasiddhi.domain.usecase.IncrementPurashcharanaUseCase],
 * called by the recitation evaluator after a verified japa. That absence is the feature.
 *
 * State is driven entirely by the observed flow, so the screen updates itself after an
 * enroll or a withdraw; the one-shot [events] stream exists only for the snackbars, which
 * must fire once and never replay on rotation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurashcharanaViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getActivePurashcharanaUseCase: GetActivePurashcharanaUseCase,
    private val enrollPurashcharanaUseCase: EnrollPurashcharanaUseCase,
    private val withdrawPurashcharanaUseCase: WithdrawPurashcharanaUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PurashcharanaUiState>(PurashcharanaUiState.Loading)
    val uiState: StateFlow<PurashcharanaUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PurashcharanaEvent>()
    val events: SharedFlow<PurashcharanaEvent> = _events.asSharedFlow()

    /** Cached so the two actions do not have to re-resolve the session. */
    private var activeUserId: Long? = null

    init {
        observeActiveSankalpa()
    }

    private fun observeActiveSankalpa() {
        viewModelScope.launch {
            getCurrentUserUseCase().flatMapLatest { user ->
                activeUserId = user?.id
                if (user == null) {
                    flowOf(PurashcharanaUiState.Error(R.string.home_error_no_profile))
                } else {
                    getActivePurashcharanaUseCase(user.id)
                        .map<Purashcharana?, PurashcharanaUiState> { PurashcharanaUiState.Success(it) }
                        .catch { emit(PurashcharanaUiState.Error(R.string.error_unknown)) }
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Takes the Purashcharana sankalpa. Called only after the confirmation dialog: a vow
     * of 24,00,000 japa must never be the result of one stray tap.
     */
    fun takeSankalpa() {
        val userId = activeUserId
        if (userId == null) {
            viewModelScope.launch { _events.emit(PurashcharanaEvent.ShowError()) }
            return
        }
        viewModelScope.launch {
            enrollPurashcharanaUseCase(userId)
                .onSuccess { _events.emit(PurashcharanaEvent.Enrolled) }
                .onFailure { e -> _events.emit(PurashcharanaEvent.ShowError(e.message)) }
        }
    }

    /**
     * Sets the sankalpa aside. The japa already counted is kept as history by the
     * repository, so this is reversible in spirit even though the vow itself is not.
     */
    fun withdrawSankalpa() {
        val userId = activeUserId
        if (userId == null) {
            viewModelScope.launch { _events.emit(PurashcharanaEvent.ShowError()) }
            return
        }
        viewModelScope.launch {
            withdrawPurashcharanaUseCase(userId)
                .onSuccess { _events.emit(PurashcharanaEvent.Withdrawn) }
                .onFailure { e -> _events.emit(PurashcharanaEvent.ShowError(e.message)) }
        }
    }
}
