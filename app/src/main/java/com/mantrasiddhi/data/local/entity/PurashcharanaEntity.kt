/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.entity

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
    indices = [Index(value = ["userId"])]
)
data class PurashcharanaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val mantraAksharaCount: Int,
    val targetJapaCount: Long,
    val currentJapaCount: Long,
    val startedAtMillis: Long,
    val completedAtMillis: Long?,
    val isActive: Boolean
) {
    fun toDomain(): Purashcharana {
        return Purashcharana(
            id = id,
            userId = userId,
            mantraAksharaCount = mantraAksharaCount,
            targetJapaCount = targetJapaCount,
            currentJapaCount = currentJapaCount,
            startedAtMillis = startedAtMillis,
            completedAtMillis = completedAtMillis,
            isActive = isActive
        )
    }

    companion object {
        fun fromDomain(domain: Purashcharana): PurashcharanaEntity {
            return PurashcharanaEntity(
                id = domain.id,
                userId = domain.userId,
                mantraAksharaCount = domain.mantraAksharaCount,
                targetJapaCount = domain.targetJapaCount,
                currentJapaCount = domain.currentJapaCount,
                startedAtMillis = domain.startedAtMillis,
                completedAtMillis = domain.completedAtMillis,
                isActive = domain.isActive
            )
        }
    }
}
