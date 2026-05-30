/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.local.DemoDataSeeder
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.SessionRepository
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SettingsEvent {
    data object DemoDataReset : SettingsEvent()
    data object ProfileLoggedOut : SettingsEvent()
    data class Error(val message: String) : SettingsEvent()
}

/**
 * ViewModel to manage app configuration and profile switching.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val sessionRepository: SessionRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val demoDataSeeder: DemoDataSeeder
) : ViewModel() {

    val currentUser: StateFlow<UserProfile?> = getCurrentUserUseCase()

    val themeMode: StateFlow<String> = preferencesDataStore.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val languageDisplay: StateFlow<String> = preferencesDataStore.languageDisplayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "BOTH")

    val sensitivityThreshold: StateFlow<Double> = preferencesDataStore.recitationSensitivityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_SENSITIVITY)

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesDataStore.setThemeMode(mode)
        }
    }

    fun setLanguageDisplay(display: String) {
        viewModelScope.launch {
            preferencesDataStore.setLanguageDisplay(display)
        }
    }

    fun setSensitivityThreshold(threshold: Double) {
        viewModelScope.launch {
            preferencesDataStore.setRecitationSensitivity(threshold)
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionRepository.logout()
            _events.emit(SettingsEvent.ProfileLoggedOut)
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            try {
                // Seed mock history data
                demoDataSeeder.seed()
                // Clear the active session and log out the user so they can select a re-seeded profile
                sessionRepository.logout()
                _events.emit(SettingsEvent.DemoDataReset)
            } catch (e: Exception) {
                _events.emit(SettingsEvent.Error(e.message ?: "Failed to reset demo data"))
            }
        }
    }

    private companion object {
        /** Initial sensitivity shown while the stored preference loads. */
        const val DEFAULT_SENSITIVITY: Double = 0.85
    }
}

