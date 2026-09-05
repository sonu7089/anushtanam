/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.auth

import androidx.annotation.StringRes

/**
 * State for the auth flow (parent + child screens share one ViewModel).
 *
 * @property isLoading a network auth call is in flight.
 * @property errorMessageResId last error to surface inline (null when none). Held as a
 *   resource id rather than text so the ViewModel never needs a Context and every
 *   sentence stays in `strings.xml`.
 * @property errorDetail the failing call's own message when Firebase supplied one. It is
 *   English-only and not localizable, so the screen prefers it purely because it is more
 *   specific than the generic fallback ("wrong password", "email already in use", …).
 * @property generatedChildId set after a successful child sign-up so the UI can show the
 *   new 12-digit ID prominently before the child proceeds into the app.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    @StringRes val errorMessageResId: Int? = null,
    val errorDetail: String? = null,
    val generatedChildId: String? = null
)
