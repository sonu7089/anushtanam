/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.home

import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val user: UserProfile,
        val todayCount: Int,
        val activeTarget: Target?,
        val streakDays: Int,
        val childrenProgress: List<ChildProgress> = emptyList()
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
