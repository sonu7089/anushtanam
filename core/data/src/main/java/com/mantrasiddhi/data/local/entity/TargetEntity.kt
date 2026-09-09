/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.model.Target

@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val setByUserId: Long,
    val targetRecitationCount: Int,
    val currentRecitationCount: Int,
    val rewardType: String,
    val rewardDescription: String,
    val isCompleted: Boolean,
    @ColumnInfo(defaultValue = "''") val stableId: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "'gayatri'") val mantraId: String = "gayatri",
    @ColumnInfo(defaultValue = "0") val startedAtMillis: Long = System.currentTimeMillis(),
    val endedAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(defaultValue = "0") val baselineCount: Long = currentRecitationCount.toLong()
) {
    fun toDomain(): Target {
        return Target(
            id = id,
            userId = userId,
            setByUserId = setByUserId,
            targetRecitationCount = targetRecitationCount,
            currentRecitationCount = currentRecitationCount,
            rewardType = RewardType.fromName(rewardType),
            rewardDescription = rewardDescription,
            isCompleted = isCompleted, stableId = stableId, mantraId = mantraId,
            startedAtMillis = startedAtMillis, endedAtMillis = endedAtMillis, isActive = isActive, baselineCount = baselineCount
        )
    }

    companion object {
        fun fromDomain(domain: Target): TargetEntity {
            return TargetEntity(
                id = domain.id,
                userId = domain.userId,
                setByUserId = domain.setByUserId,
                targetRecitationCount = domain.targetRecitationCount,
                currentRecitationCount = domain.currentRecitationCount,
                rewardType = domain.rewardType.name,
                rewardDescription = domain.rewardDescription,
                isCompleted = domain.isCompleted, stableId = domain.stableId, mantraId = domain.mantraId,
                startedAtMillis = domain.startedAtMillis, endedAtMillis = domain.endedAtMillis, isActive = domain.isActive, baselineCount = domain.baselineCount
            )
        }
    }
}
