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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.mantrasiddhi.data.sync.AccountRecordStore
import com.mantrasiddhi.data.sync.AccountRecordSnapshot
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
    @ApplicationContext private val context: Context,
    private val accountRecords: AccountRecordStore? = null
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

    val settings: Flow<SandhyaSettings> = combine(context.dataStore.data,
        accountRecords?.observe() ?: flowOf(AccountRecordSnapshot(null))) { prefs, snapshot ->
        val cloud = snapshot.records.takeIf { snapshot.uid == accountRecords?.currentUid }.orEmpty()
        val signedIn = accountRecords?.currentUid != null
        @Suppress("UNCHECKED_CAST")
        val savedRegion = cloud["preference_sandhya_region"]?.get("value") as? Map<String, Any?>
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
            enabled = if (signedIn) cloud["preference_sandhya_enabled"]?.get("value") as? Boolean ?: false else prefs[KEY_ENABLED] ?: false,
            region = if (!signedIn) region else savedRegion?.let {
                SandhyaRegion(it["id"] as String, it["name"] as String,
                    (it["latitude"] as Number).toDouble(), (it["longitude"] as Number).toDouble(), it["zoneId"] as String)
            } ?: SandhyaRegions.DEFAULT,
            userId = prefs[KEY_USER_ID] ?: 0L,
            preparationEnabled = if (signedIn) cloud["preference_sandhya_preparation"]?.get("value") as? Boolean ?: true else prefs[KEY_PREPARATION] ?: true,
            eventEnabled = if (signedIn) cloud["preference_sandhya_event"]?.get("value") as? Boolean ?: true else prefs[KEY_EVENT] ?: true
        )
    }.distinctUntilChanged()

    val permissionAsked = context.dataStore.data.map { it[KEY_PERMISSION_ASKED] ?: false }
    suspend fun markPermissionAsked() { context.dataStore.edit { it[KEY_PERMISSION_ASKED] = true } }
    suspend fun setStage(stage: com.mantrasiddhi.domain.model.SandhyaReminderStage, enabled: Boolean) {
        val key = if (stage == com.mantrasiddhi.domain.model.SandhyaReminderStage.PREPARATION) KEY_PREPARATION else KEY_EVENT
        if (accountRecords?.preference(key.name, enabled) == true) return
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

    fun minimums(userId: Long): Flow<SandhyaMinimums> = combine(context.dataStore.data,
        accountRecords?.observe() ?: flowOf(AccountRecordSnapshot(null))) { prefs, snapshot ->
        val cloud = snapshot.records.takeIf { snapshot.uid == accountRecords?.currentUid }.orEmpty()
        val signedIn = accountRecords?.currentUid != null && accountRecords.owner(userId) == accountRecords.currentUid
        fun value(period: SandhyaPeriod) = if (signedIn)
            (cloud["preference_minimum_${period.name}"]?.get("value") as? Number)?.toLong() ?: period.minimumJapa
            else prefs[minimumKey(userId, period)] ?: period.minimumJapa
        SandhyaMinimums(value(SandhyaPeriod.PRATAH), value(SandhyaPeriod.MADHYAHNA), value(SandhyaPeriod.SAYAM))
    }

    suspend fun setMinimums(userId: Long, minimums: SandhyaMinimums) {
        require(userId > 0)
        val records = accountRecords
        if (records?.currentUid != null && records.owner(userId) == records.currentUid) {
            for (period in SandhyaPeriod.entries) records.preference("minimum_${period.name}", minimums[period])
            return
        }
        context.dataStore.edit { prefs ->
            SandhyaPeriod.entries.forEach { prefs[minimumKey(userId, it)] = minimums[it] }
        }
    }

    private fun minimumKey(userId: Long, period: SandhyaPeriod) =
        longPreferencesKey("minimum_${userId}_${period.name}")

    suspend fun setEnabled(enabled: Boolean) {
        if (accountRecords?.preference(KEY_ENABLED.name, enabled) == true) return
        context.dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setRegion(region: SandhyaRegion) {
        if (accountRecords?.preference("sandhya_region", region.cloudValue()) == true) return
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

    suspend fun seedCloud(uid: String, userId: Long) {
        val records = accountRecords ?: return
        val owner = stringPreferencesKey("cloud_settings_legacy_owner")
        var snapshot: Preferences? = null
        context.dataStore.edit { prefs ->
            if (prefs[owner] == null) prefs[owner] = uid
            if (prefs[owner] == uid) snapshot = prefs.toPreferences()
        }
        val prefs = snapshot ?: return
        for (key in listOf(KEY_ENABLED, KEY_PREPARATION, KEY_EVENT)) prefs[key]?.let {
            records.put(uid, "preference_${key.name}", mapOf("value" to it), seed = true)
        }
        for (period in SandhyaPeriod.entries) prefs[minimumKey(userId, period)]?.let {
            records.put(uid, "preference_minimum_${period.name}", mapOf("value" to it), seed = true)
        }
        if (prefs[KEY_REGION_ID] != null) {
            val region = SandhyaRegion(prefs[KEY_REGION_ID]!!, prefs[KEY_REGION_NAME] ?: SandhyaRegions.DEFAULT.displayName,
                prefs[KEY_LAT] ?: SandhyaRegions.DEFAULT.latitude, prefs[KEY_LON] ?: SandhyaRegions.DEFAULT.longitude, prefs[KEY_ZONE] ?: "")
            records.put(uid, "preference_sandhya_region", mapOf("value" to region.cloudValue()), seed = true)
        }
    }

    private fun SandhyaRegion.cloudValue() = mapOf("id" to id, "name" to displayName,
        "latitude" to latitude, "longitude" to longitude, "zoneId" to zoneId)
}
