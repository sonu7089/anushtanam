/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.GetProfilesUseCase
import com.mantrasiddhi.domain.usecase.SelectProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val getProfilesUseCase: GetProfilesUseCase,
    private val selectProfileUseCase: SelectProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<LoginEvent>()
    val uiEvent: SharedFlow<LoginEvent> = _uiEvent.asSharedFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            getProfilesUseCase()
                .catch { e ->
                    _uiState.value = LoginUiState.Error(e.message ?: "Failed to load profiles")
                }
                .collect { profiles ->
                    // Preseeded data might take a split second, wait if empty initially
                    _uiState.value = LoginUiState.Success(profiles)
                }
        }
    }

    fun selectProfile(user: UserProfile) {
        viewModelScope.launch {
            selectProfileUseCase(user)
            _uiEvent.emit(LoginEvent.NavigateToHome)
        }
    }
}
