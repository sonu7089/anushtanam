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
import com.mantrasiddhi.domain.model.SandhyaRegion
import com.mantrasiddhi.domain.model.SandhyaRegions
import com.mantrasiddhi.domain.model.SandhyaMinimums
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.minimumJapa
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Resolved Sandhya configuration. [region] always present (falls back to the default). */
data class SandhyaSettings(
    val enabled: Boolean,
    val region: SandhyaRegion,
    val userId: Long,
    val preparationEnabled: Boolean = true,
    val eventEnabled: Boolean = true
) {
    /** Timezone for solar math: the region's zone, or the device zone if unset/invalid. */
    fun zone(): ZoneId = runCatching {
        if (region.zoneId.isBlank()) ZoneId.systemDefault() else ZoneId.of(region.zoneId)
    }.getOrDefault(ZoneId.systemDefault())
}

/**
 * DataStore-backed Sandhya Vandan settings. The effective region is stored
 * self-contained (name + lat/lon + zone) so a background alarm/boot receiver can
 * read it without consulting the curated city list — and so GPS-derived regions
 * persist just like picked cities.
 */
@Singleton
class SandhyaPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sandhya_prefs")

    private companion object {
        val KEY_PREPARATION = booleanPreferencesKey("sandhya_preparation")
        val KEY_EVENT = booleanPreferencesKey("sandhya_event")
        val KEY_PERMISSION_ASKED = booleanPreferencesKey("sandhya_permission_asked")
        val KEY_ENABLED = booleanPreferencesKey("sandhya_enabled")
        val KEY_REGION_ID = stringPreferencesKey("sandhya_region_id")
        val KEY_REGION_NAME = stringPreferencesKey("sandhya_region_name")
        val KEY_LAT = doublePreferencesKey("sandhya_lat")
        val KEY_LON = doublePreferencesKey("sandhya_lon")
        val KEY_ZONE = stringPreferencesKey("sandhya_zone")
        val KEY_USER_ID = longPreferencesKey("sandhya_user_id")
    }

    val settings: Flow<SandhyaSettings> = context.dataStore.data.map { prefs ->
        val region = if (prefs[KEY_REGION_ID] != null) {
            SandhyaRegion(
                id = prefs[KEY_REGION_ID]!!,
                displayName = prefs[KEY_REGION_NAME] ?: SandhyaRegions.DEFAULT.displayName,
                latitude = prefs[KEY_LAT] ?: SandhyaRegions.DEFAULT.latitude,
                longitude = prefs[KEY_LON] ?: SandhyaRegions.DEFAULT.longitude,
                zoneId = prefs[KEY_ZONE] ?: ""
            )
        } else {
            SandhyaRegions.DEFAULT
        }
        SandhyaSettings(
            enabled = prefs[KEY_ENABLED] ?: false,
            region = region,
            userId = prefs[KEY_USER_ID] ?: 0L,
            preparationEnabled = prefs[KEY_PREPARATION] ?: true,
            eventEnabled = prefs[KEY_EVENT] ?: true
        )
    }.distinctUntilChanged()

    val permissionAsked = context.dataStore.data.map { it[KEY_PERMISSION_ASKED] ?: false }
    suspend fun markPermissionAsked() { context.dataStore.edit { it[KEY_PERMISSION_ASKED] = true } }
    suspend fun setStage(stage: com.mantrasiddhi.domain.model.SandhyaReminderStage, enabled: Boolean) {
        context.dataStore.edit { it[if (stage == com.mantrasiddhi.domain.model.SandhyaReminderStage.PREPARATION) KEY_PREPARATION else KEY_EVENT] = enabled }
    }
    private fun deliveryKey(userId: Long, period: SandhyaPeriod, stage: com.mantrasiddhi.domain.model.SandhyaReminderStage) =
        longPreferencesKey("delivered_${userId}_${period.name}_${stage.name}")
    suspend fun delivered(userId: Long, period: SandhyaPeriod, stage: com.mantrasiddhi.domain.model.SandhyaReminderStage): Long =
        context.dataStore.data.first()[deliveryKey(userId, period, stage)] ?: 0L
    suspend fun deliveredDate(userId: Long, period: SandhyaPeriod, stage: com.mantrasiddhi.domain.model.SandhyaReminderStage): String? =
        context.dataStore.data.first()[stringPreferencesKey("delivery_date_${userId}_${period.name}_${stage.name}")]
    suspend fun recordDelivery(userId: Long, period: SandhyaPeriod, stage: com.mantrasiddhi.domain.model.SandhyaReminderStage, event: Long, dateKey: String) {
        context.dataStore.edit {
            it[deliveryKey(userId, period, stage)] = event
            it[stringPreferencesKey("delivery_date_${userId}_${period.name}_${stage.name}")] = dateKey
        }
    }

    fun deliveryChanges(userId: Long) = context.dataStore.data.map { prefs ->
        prefs.asMap().filterKeys { it.name.startsWith("delivered_${userId}_") || it.name.startsWith("delivery_date_${userId}_") }
    }.distinctUntilChanged()

    suspend fun current(): SandhyaSettings = settings.first()

    fun minimums(userId: Long): Flow<SandhyaMinimums> = context.dataStore.data.map { prefs ->
        fun value(period: SandhyaPeriod) = prefs[minimumKey(userId, period)] ?: period.minimumJapa
        SandhyaMinimums(value(SandhyaPeriod.PRATAH), value(SandhyaPeriod.MADHYAHNA), value(SandhyaPeriod.SAYAM))
    }

    suspend fun setMinimums(userId: Long, minimums: SandhyaMinimums) {
        require(userId > 0)
        context.dataStore.edit { prefs ->
            SandhyaPeriod.entries.forEach { prefs[minimumKey(userId, it)] = minimums[it] }
        }
    }

    private fun minimumKey(userId: Long, period: SandhyaPeriod) =
        longPreferencesKey("minimum_${userId}_${period.name}")

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setRegion(region: SandhyaRegion) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REGION_ID] = region.id
            prefs[KEY_REGION_NAME] = region.displayName
            prefs[KEY_LAT] = region.latitude
            prefs[KEY_LON] = region.longitude
            prefs[KEY_ZONE] = region.zoneId
        }
    }

    suspend fun setUserId(userId: Long) {
        context.dataStore.edit { it[KEY_USER_ID] = userId }
    }
}
