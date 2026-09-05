/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Domain-facing access to user-tunable app preferences.
 *
 * Keeps the domain layer free of Android/DataStore types: the data layer adapts
 * the concrete preferences store to this interface.
 */
interface AppPreferencesRepository {

    /**
     * Recitation sensitivity in the range 0.0..1.0. Drives the evaluation pass
     * threshold — higher values demand a more accurate recitation to pass.
     */
    val recitationSensitivity: Flow<Double>

    /**
     * Whether the user has *explicitly* chosen a recitation sensitivity. When `false`
     * the evaluation engine may auto-adapt the pass gate to the learner's level; an
     * explicit choice always overrides the automatic level (§4.3: explicit > auto).
     */
    val isRecitationSensitivityExplicit: Flow<Boolean>

    /**
     * Whether the opt-in Advanced/Vedic mode (swara/pitch accuracy, §5.5) is enabled.
     * Default `false`; when off, no pitch analysis runs and scoring is unchanged.
     */
    val advancedVedicMode: Flow<Boolean>
}
