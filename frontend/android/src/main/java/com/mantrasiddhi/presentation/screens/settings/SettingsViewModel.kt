/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.mantrasiddhi.domain.repository.UserRepository
import com.mantrasiddhi.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException

sealed class SettingsEvent {
    data object ProfileLoggedOut : SettingsEvent()
    data class Error(val message: String) : SettingsEvent()
}

/**
 * ViewModel to manage app configuration and profile switching.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val signOutUseCase: SignOutUseCase,
    private val users: UserRepository,
    private val session: SessionRepository
) : ViewModel() {

    val currentUser: StateFlow<UserProfile?> = getCurrentUserUseCase()

    val themeMode: StateFlow<String> = preferencesDataStore.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val languageDisplay: StateFlow<String> = preferencesDataStore.languageDisplayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "BOTH")

    /**
     * Swara (pitch-accent) assessment. Off by default: EvaluateRecitationUseCase skips the
     * swara layer entirely while this is false, so the Abhyas screen shows no swara card
     * until the sadhak asks for it.
     */
    val advancedVedicMode: StateFlow<Boolean> = preferencesDataStore.advancedVedicModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    fun setAdvancedVedicMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setAdvancedVedicMode(enabled)
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Sign out of the Firebase cloud session and clear the local session.
            signOutUseCase()
            _events.emit(SettingsEvent.ProfileLoggedOut)
        }
    }

    fun setUseGooglePhoto(enabled: Boolean) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            try {
                val saved = users.getById(user.id) ?: return@launch
                val updated = saved.copy(useGooglePhoto = enabled)
                users.insert(updated)
                if (session.currentUser.value?.id == updated.id) session.login(updated)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _events.emit(SettingsEvent.Error("We could not save that choice. Please try again.")) }
        }
    }
}

