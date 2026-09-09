package com.mantrasiddhi.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.practiceGuideStore by preferencesDataStore(name = "practice_guide")

/** Each profile and content version keeps its own record. No acknowledgement is inferred. */
data class PracticeGuideRecord(
    val guidanceVersion: Int,
    val shownAt: Long? = null,
    val openedAt: Long? = null,
    val acknowledgedAt: Long? = null
)

@Singleton
class PracticeGuidePreferences @Inject constructor(@ApplicationContext private val context: Context) {
    fun observe(profileId: Long, version: Int) = context.practiceGuideStore.data.map {
        PracticeGuideRecord(version, it[key(profileId, version, "shown")],
            it[key(profileId, version, "opened")], it[key(profileId, version, "acknowledged")])
    }

    suspend fun markShown(profileId: Long, version: Int, openedByUser: Boolean) {
        context.practiceGuideStore.edit {
            val now = System.currentTimeMillis()
            val shown = key(profileId, version, "shown")
            if (it[shown] == null) it[shown] = now
            val opened = key(profileId, version, "opened")
            if (openedByUser && it[opened] == null) it[opened] = now
        }
    }

    suspend fun acknowledge(profileId: Long, version: Int) {
        context.practiceGuideStore.edit {
            val acknowledged = key(profileId, version, "acknowledged")
            if (it[acknowledged] == null) it[acknowledged] = System.currentTimeMillis()
        }
    }

    private fun key(profileId: Long, version: Int, field: String) =
        longPreferencesKey("profile_${profileId}_v${version}_$field")
}
