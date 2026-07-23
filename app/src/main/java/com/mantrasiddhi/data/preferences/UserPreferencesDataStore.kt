/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_LANGUAGE_DISPLAY = stringPreferencesKey("language_display")
        private val KEY_RECITATION_SENSITIVITY = doublePreferencesKey("recitation_sensitivity")
        private val KEY_ADVANCED_VEDIC_MODE = booleanPreferencesKey("advanced_vedic_mode")

        /** Prefix for the firebaseUid -> local Room profile id mapping (Phase B bridge). */
        private const val FIREBASE_LOCAL_ID_PREFIX = "firebase_local_id_"
    }

    /**
     * Returns the local Room profile id previously mapped to [firebaseUid], or null if
     * this cloud account has not yet been bridged to a local profile on this device.
     */
    suspend fun getLocalProfileId(firebaseUid: String): Long? {
        val key = longPreferencesKey("$FIREBASE_LOCAL_ID_PREFIX$firebaseUid")
        return context.dataStore.data.first()[key]
    }

    /** Persists the firebaseUid -> local Room profile id mapping (Phase B bridge). */
    suspend fun setLocalProfileId(firebaseUid: String, localId: Long) {
        val key = longPreferencesKey("$FIREBASE_LOCAL_ID_PREFIX$firebaseUid")
        context.dataStore.edit { it[key] = localId }
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_THEME] ?: "SYSTEM"
    }

    val languageDisplayFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_LANGUAGE_DISPLAY] ?: "BOTH"
    }

    val recitationSensitivityFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_RECITATION_SENSITIVITY] ?: 0.85
    }

    /** Emits whether the user has explicitly persisted a recitation-sensitivity value. */
    val recitationSensitivityIsSetFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences.contains(KEY_RECITATION_SENSITIVITY)
    }

    /** Whether the opt-in Advanced/Vedic (swara/pitch) mode is enabled. Default off. */
    val advancedVedicModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ADVANCED_VEDIC_MODE] ?: false
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME] = mode
        }
    }

    suspend fun setLanguageDisplay(display: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LANGUAGE_DISPLAY] = display
        }
    }

    suspend fun setRecitationSensitivity(sensitivity: Double) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RECITATION_SENSITIVITY] = sensitivity
        }
    }

    suspend fun setAdvancedVedicMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ADVANCED_VEDIC_MODE] = enabled
        }
    }
}
