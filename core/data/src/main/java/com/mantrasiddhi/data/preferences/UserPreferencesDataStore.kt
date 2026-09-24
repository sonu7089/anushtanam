/*
 * Anushtanam - Vedic mantra recitation app
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.mantrasiddhi.data.sync.AccountRecordStore
import com.mantrasiddhi.data.sync.AccountRecordSnapshot
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRecords: AccountRecordStore? = null
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

    suspend fun getOwnerOfLocalProfile(localId: Long): String? =
        context.dataStore.data.first().asMap().entries.firstOrNull {
            it.key.name.startsWith(FIREBASE_LOCAL_ID_PREFIX) && it.value == localId
        }?.key?.name?.removePrefix(FIREBASE_LOCAL_ID_PREFIX)

    private fun <T> setting(key: Preferences.Key<T>, fallback: T): Flow<T> = combine(
        context.dataStore.data, accountRecords?.observe() ?: flowOf(AccountRecordSnapshot(null))
    ) { prefs, snapshot ->
        val cloud = snapshot.records.takeIf { snapshot.uid == accountRecords?.currentUid }.orEmpty()
        @Suppress("UNCHECKED_CAST")
        if (accountRecords?.currentUid != null) {
            val value = cloud["preference_${key.name}"]?.get("value")
            (if (fallback is Double && value is Number) value.toDouble() as T else value as? T) ?: fallback
        }
        else prefs[key] ?: fallback
    }

    val themeModeFlow: Flow<String> = setting(KEY_THEME, "SYSTEM")

    val languageDisplayFlow: Flow<String> = setting(KEY_LANGUAGE_DISPLAY, "BOTH")

    val recitationSensitivityFlow: Flow<Double> = setting(KEY_RECITATION_SENSITIVITY, 0.85)

    /** Emits whether the user has explicitly persisted a recitation-sensitivity value. */
    val recitationSensitivityIsSetFlow: Flow<Boolean> = combine(context.dataStore.data,
        accountRecords?.observe() ?: flowOf(AccountRecordSnapshot(null))) { prefs, snapshot ->
        val cloud = snapshot.records.takeIf { snapshot.uid == accountRecords?.currentUid }.orEmpty()
        if (accountRecords?.currentUid != null) cloud.containsKey("preference_recitation_sensitivity")
        else prefs.contains(KEY_RECITATION_SENSITIVITY)
    }

    /** Whether the opt-in Advanced/Vedic (swara/pitch) mode is enabled. Default off. */
    val advancedVedicModeFlow: Flow<Boolean> = setting(KEY_ADVANCED_VEDIC_MODE, false)

    suspend fun setThemeMode(mode: String) {
        if (accountRecords?.preference(KEY_THEME.name, mode) == true) return
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME] = mode
        }
    }

    suspend fun setLanguageDisplay(display: String) {
        if (accountRecords?.preference(KEY_LANGUAGE_DISPLAY.name, display) == true) return
        context.dataStore.edit { preferences ->
            preferences[KEY_LANGUAGE_DISPLAY] = display
        }
    }

    suspend fun setRecitationSensitivity(sensitivity: Double) {
        if (accountRecords?.preference(KEY_RECITATION_SENSITIVITY.name, sensitivity) == true) return
        context.dataStore.edit { preferences ->
            preferences[KEY_RECITATION_SENSITIVITY] = sensitivity
        }
    }

    suspend fun setAdvancedVedicMode(enabled: Boolean) {
        if (accountRecords?.preference(KEY_ADVANCED_VEDIC_MODE.name, enabled) == true) return
        context.dataStore.edit { preferences ->
            preferences[KEY_ADVANCED_VEDIC_MODE] = enabled
        }
    }

    /** Claim legacy device preferences once; a different account starts with its own defaults. */
    suspend fun seedCloud(uid: String) {
        val records = accountRecords ?: return
        val owner = stringPreferencesKey("cloud_settings_legacy_owner")
        var snapshot: Preferences? = null
        context.dataStore.edit { prefs ->
            if (prefs[owner] == null) prefs[owner] = uid
            if (prefs[owner] == uid) snapshot = prefs.toPreferences()
        }
        val prefs = snapshot ?: return
        for (key in listOf(KEY_THEME, KEY_LANGUAGE_DISPLAY, KEY_RECITATION_SENSITIVITY, KEY_ADVANCED_VEDIC_MODE)) {
            prefs.asMap()[key]?.let { records.put(uid, "preference_${key.name}", mapOf("value" to it), seed = true) }
        }
    }
}
