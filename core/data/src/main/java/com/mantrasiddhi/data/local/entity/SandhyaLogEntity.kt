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
import com.mantrasiddhi.domain.model.SandhyaLog
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.SandhyaStatus

/**
 * One scheduled Sandhya occurrence. The unique (userId, dateKey, period) index
 * guarantees a single row per juncture per day per profile, so a re-fired reminder
 * (inserted with IGNORE) can never duplicate or overwrite an existing outcome.
 */
@Entity(
    tableName = "sandhya_logs",
    indices = [
        Index(value = ["userId", "dateKey", "period"], unique = true),
        Index(value = ["userId"])
    ]
)
data class SandhyaLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val period: String,              // SandhyaPeriod name
    val scheduledEpochMillis: Long,
    val status: String,              // SandhyaStatus name
    val observedEpochMillis: Long?,
    val dateKey: String              // yyyyMMdd local
) {
    fun toDomain(): SandhyaLog = SandhyaLog(
        id = id,
        userId = userId,
        period = SandhyaPeriod.valueOf(period),
        scheduledEpochMillis = scheduledEpochMillis,
        status = SandhyaStatus.valueOf(status),
        observedEpochMillis = observedEpochMillis,
        dateKey = dateKey
    )

    companion object {
        fun fromDomain(domain: SandhyaLog): SandhyaLogEntity = SandhyaLogEntity(
            id = domain.id,
            userId = domain.userId,
            period = domain.period.name,
            scheduledEpochMillis = domain.scheduledEpochMillis,
            status = domain.status.name,
            observedEpochMillis = domain.observedEpochMillis,
            dateKey = domain.dateKey
        )
    }
}
