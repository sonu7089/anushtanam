/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.auth

/** One-shot events emitted by [AuthViewModel]. */
sealed interface AuthEvent {
    /** Auth succeeded and the local profile was synced — proceed into the app. */
    data object NavigateToHome : AuthEvent
    /** A parent password-reset email was sent. */
    data object PasswordResetSent : AuthEvent
    data class ShowMessage(val message: String) : AuthEvent
}
