/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.login

import com.mantrasiddhi.domain.model.UserProfile

sealed interface LoginUiState {
    data object Loading : LoginUiState
    data class Success(val profiles: List<UserProfile>) : LoginUiState
    data class Error(val message: String) : LoginUiState
}
