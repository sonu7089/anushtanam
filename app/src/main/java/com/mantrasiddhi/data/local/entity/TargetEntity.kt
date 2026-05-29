/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mantrasiddhi.domain.model.Target

@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val setByUserId: Long,
    val targetRecitationCount: Int,
    val currentRecitationCount: Int,
    val moneyAmount: Double,
    val isCompleted: Boolean
) {
    fun toDomain(): Target {
        return Target(
            id = id,
            userId = userId,
            setByUserId = setByUserId,
            targetRecitationCount = targetRecitationCount,
            currentRecitationCount = currentRecitationCount,
            moneyAmount = moneyAmount,
            isCompleted = isCompleted
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
                moneyAmount = domain.moneyAmount,
                isCompleted = domain.isCompleted
            )
        }
    }
}
