/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.domain.usecase.SendParentPasswordResetUseCase
import com.mantrasiddhi.domain.usecase.SignInChildUseCase
import com.mantrasiddhi.domain.usecase.SignInParentUseCase
import com.mantrasiddhi.domain.usecase.SignUpChildUseCase
import com.mantrasiddhi.domain.usecase.SignUpParentUseCase
import com.mantrasiddhi.domain.usecase.SyncSignedInUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the welcome / parent / child auth screens. On a successful sign-in/up it
 * bridges the cloud account to a local Room profile ([SyncSignedInUserUseCase]) before
 * emitting [AuthEvent.NavigateToHome], so the rest of the app keeps working unchanged.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signUpParentUseCase: SignUpParentUseCase,
    private val signInParentUseCase: SignInParentUseCase,
    private val signUpChildUseCase: SignUpChildUseCase,
    private val signInChildUseCase: SignInChildUseCase,
    private val sendParentPasswordResetUseCase: SendParentPasswordResetUseCase,
    private val syncSignedInUserUseCase: SyncSignedInUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    /** The child account awaiting the "share your ID then continue" step. */
    private var pendingChildAccount: AuthAccount? = null

    fun clearError() {
        _uiState.update { it.copy(errorMessageResId = null, errorDetail = null) }
    }

    fun signInParent(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            fail(R.string.auth_error_missing_credentials)
            return
        }
        run { signInParentUseCase(email, password) }
    }

    fun signUpParent(email: String, password: String, displayName: String) {
        if (email.isBlank() || password.length < 6 || displayName.isBlank()) {
            fail(R.string.auth_error_signup_incomplete)
            return
        }
        run { signUpParentUseCase(email, password, displayName) }
    }

    fun signInChild(code: String, pin: String) {
        run { signInChildUseCase(code, pin) }
    }

    /**
     * Child sign-up. On success the account is signed in immediately; we surface the
     * generated 12-digit ID and wait for the child to tap Continue ([continueAfterChildSignup]).
     */
    fun signUpChild(displayName: String, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageResId = null, errorDetail = null) }
            signUpChildUseCase(displayName, pin)
                .onSuccess { account ->
                    pendingChildAccount = account
                    _uiState.update {
                        it.copy(isLoading = false, generatedChildId = account.linkCode)
                    }
                }
                .onFailure { e -> failWith(e) }
        }
    }

    fun continueAfterChildSignup() {
        val account = pendingChildAccount ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            syncSignedInUserUseCase(account)
            pendingChildAccount = null
            _uiState.update { it.copy(isLoading = false, generatedChildId = null) }
            _events.emit(AuthEvent.NavigateToHome)
        }
    }

    fun sendParentPasswordReset(email: String) {
        if (email.isBlank()) {
            fail(R.string.auth_error_reset_email_missing)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageResId = null, errorDetail = null) }
            sendParentPasswordResetUseCase(email)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(AuthEvent.PasswordResetSent)
                }
                .onFailure { e -> failWith(e) }
        }
    }

    /** Runs an auth call, syncs the local profile, then navigates home. */
    private fun run(call: suspend () -> Result<AuthAccount>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageResId = null, errorDetail = null) }
            call()
                .onSuccess { account ->
                    syncSignedInUserUseCase(account)
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(AuthEvent.NavigateToHome)
                }
                .onFailure { e -> failWith(e) }
        }
    }

    /** Surfaces a validation problem we can word ourselves. */
    private fun fail(@StringRes messageResId: Int) {
        _uiState.update {
            it.copy(isLoading = false, errorMessageResId = messageResId, errorDetail = null)
        }
    }

    /**
     * Surfaces a failed auth call. Firebase's own message ("wrong password", "email already
     * in use") is far more useful than any sentence we could write here, so it is carried
     * alongside the localizable fallback and the screen picks the detail when present.
     */
    private fun failWith(e: Throwable) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessageResId = R.string.auth_error_generic,
                errorDetail = e.message
            )
        }
    }
}
