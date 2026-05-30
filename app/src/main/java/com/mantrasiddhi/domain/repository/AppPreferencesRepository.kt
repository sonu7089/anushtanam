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
}
