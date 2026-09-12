/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mantrasiddhi.domain.model.Purashcharana

/**
 * A Purashcharana sankalpa row. Withdrawn sankalpas stay in the table with
 * `isActive = 0`, so a sadhak who stops and starts again keeps the record of what they
 * had already recited; the `userId` index keeps the "is one active?" lookup cheap, since
 * it runs on every verified recitation.
 */
@Entity(
    tableName = "purashcharana",
    indices = [Index(value = ["userId"]), Index(value = ["userId", "stableId"], unique = true)]
)
data class PurashcharanaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    // The mantra under sankalpa. Vows taken before the app carried a catalog predate the
    // column, so the v6 -> v7 migration backfills them with the mantra it shipped with;
    // the default is declared here too so the schema and the migration agree exactly.
    @ColumnInfo(defaultValue = LEGACY_MANTRA_ID) val mantraId: String,
    val mantraAksharaCount: Int,
    val targetJapaCount: Long,
    val currentJapaCount: Long,
    val startedAtMillis: Long,
    val completedAtMillis: Long?,
    val isActive: Boolean,
    @ColumnInfo(defaultValue = "''") val stableId: String = java.util.UUID.randomUUID().toString(),
    val endedAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val baselineCount: Long = currentJapaCount
) {
    fun toDomain(): Purashcharana {
        return Purashcharana(
            id = id,
            userId = userId,
            mantraId = mantraId,
            mantraAksharaCount = mantraAksharaCount,
            targetJapaCount = targetJapaCount,
            currentJapaCount = currentJapaCount,
            startedAtMillis = startedAtMillis,
            completedAtMillis = completedAtMillis,
            isActive = isActive, stableId = stableId, endedAtMillis = endedAtMillis, baselineCount = baselineCount
        )
    }

    companion object {
        /**
         * Mantra assumed for sankalpas taken before the app carried a mantra catalog. Kept
         * as a literal rather than read from the catalog: it records what those vows
         * actually were, and must not follow a later change of default mantra.
         */
        const val LEGACY_MANTRA_ID = "gayatri"

        fun fromDomain(domain: Purashcharana): PurashcharanaEntity {
            return PurashcharanaEntity(
                id = domain.id,
                userId = domain.userId,
                mantraId = domain.mantraId,
                mantraAksharaCount = domain.mantraAksharaCount,
                targetJapaCount = domain.targetJapaCount,
                currentJapaCount = domain.currentJapaCount,
                startedAtMillis = domain.startedAtMillis,
                completedAtMillis = domain.completedAtMillis,
                isActive = domain.isActive, stableId = domain.stableId, endedAtMillis = domain.endedAtMillis, baselineCount = domain.baselineCount
            )
        }
    }
}
