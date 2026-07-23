/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.auth

/**
 * State for the auth flow (parent + child screens share one ViewModel).
 *
 * @property isLoading a network auth call is in flight.
 * @property errorMessage last error to surface inline (null when none).
 * @property generatedChildId set after a successful child sign-up so the UI can show the
 *   new 12-digit ID prominently before the child proceeds into the app.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val generatedChildId: String? = null
)
