/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.repository

import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Adapts [UserPreferencesDataStore] to the domain [AppPreferencesRepository].
 */
class AppPreferencesRepositoryImpl @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore
) : AppPreferencesRepository {

    override val recitationSensitivity: Flow<Double> =
        preferencesDataStore.recitationSensitivityFlow
}
