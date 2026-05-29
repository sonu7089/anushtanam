/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mantrasiddhi.domain.model.Savings

@Entity(tableName = "savings")
data class SavingsEntity(
    @PrimaryKey val userId: Long,
    val totalSaved: Double = 0.0,
    val totalLocked: Double = 0.0,
    val totalUnlocked: Double = 0.0
) {
    fun toDomain(): Savings {
        return Savings(
            userId = userId,
            totalSaved = totalSaved,
            totalLocked = totalLocked,
            totalUnlocked = totalUnlocked
        )
    }

    companion object {
        fun fromDomain(domain: Savings): SavingsEntity {
            return SavingsEntity(
                userId = domain.userId,
                totalSaved = domain.totalSaved,
                totalLocked = domain.totalLocked,
                totalUnlocked = domain.totalUnlocked
            )
        }
    }
}
