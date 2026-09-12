/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.home

import androidx.annotation.StringRes

import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val user: UserProfile,
        val todayCount: Int,
        val activeTarget: Target?,
        val streakDays: Int,
        val childrenProgress: List<ChildProgress> = emptyList(),
        /**
         * The active Purashcharana, or `null` when none has been taken — and also when it
         * could not be loaded. The dashboard must survive a failure in this one card, so
         * the Home card simply falls back to its "not enrolled" invitation.
         */
        val purashcharana: Purashcharana? = null,
        val todayJapa: String? = null,
        val statsLoading: Boolean = false, val statsFailed: Boolean = false,
        val japaLoading: Boolean = false, val japaFailed: Boolean = false,
        val vowLoading: Boolean = false, val vowFailed: Boolean = false
    ) : HomeUiState

    /**
     * A resource id rather than a resolved string: the ViewModel has no Context and must
     * not bake English into state. The composable resolves it with `stringResource`.
     */
    data class Error(@StringRes val messageResId: Int) : HomeUiState
}
