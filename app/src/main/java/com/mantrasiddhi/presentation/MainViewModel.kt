/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation

import androidx.lifecycle.ViewModel
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    getCurrentUserUseCase: GetCurrentUserUseCase
) : ViewModel() {
    val currentUser = getCurrentUserUseCase()
}
