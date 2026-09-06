/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Adapts [UserPreferencesDataStore] to the domain [AppPreferencesRepository].
 */
class AppPreferencesRepositoryImpl @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore
) : AppPreferencesRepository {

    // The evaluation sensitivity is fixed at 0.85 and is no longer user-tunable
    // (the Settings slider was removed). Reporting it as an explicit preference
    // makes the evaluation engine always resolve the pass gate to this constant,
    // regardless of any value previously stored in preferences.
    override val recitationSensitivity: Flow<Double> = flowOf(FIXED_SENSITIVITY)

    override val isRecitationSensitivityExplicit: Flow<Boolean> = flowOf(true)

    override val advancedVedicMode: Flow<Boolean> =
        preferencesDataStore.advancedVedicModeFlow

    private companion object {
        /** Hardcoded, non-configurable evaluation sensitivity (pass threshold). */
        const val FIXED_SENSITIVITY: Double = 0.85
    }
}
