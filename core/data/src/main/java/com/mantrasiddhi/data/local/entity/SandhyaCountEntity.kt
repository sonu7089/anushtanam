package com.mantrasiddhi.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mantrasiddhi.domain.model.*

@Entity(tableName = "sandhya_counts", primaryKeys = ["userId", "dateKey", "period"])
data class SandhyaCountEntity(
    val userId: Long,
    val dateKey: String,
    val period: String,
    val quantity: Long,
    val revision: Long,
    val zoneId: String
) {
    fun toDomain() = SandhyaCount(userId, dateKey, SandhyaPeriod.valueOf(period), quantity, revision, zoneId)
}

@Entity(tableName = "sandhya_count_changes", indices = [Index(value = ["userId", "dateKey", "period"])])
data class SandhyaCountChangeEntity(
    @PrimaryKey val id: String,
    val userId: Long,
    val dateKey: String,
    val period: String,
    val zoneId: String,
    val mode: String,
    val previousQuantity: Long?,
    val quantity: Long,
    val revision: Long,
    val createdAtMillis: Long
) {
    fun toDomain() = SandhyaCountChange(id, userId, dateKey, SandhyaPeriod.valueOf(period), zoneId,
        SandhyaCountMode.valueOf(mode), previousQuantity, quantity, revision, createdAtMillis)
}

@Entity(tableName = "sandhya_count_drafts", indices = [Index(value = ["userId"], unique = true)])
data class SandhyaCountDraftEntity(
    @PrimaryKey val id: String,
    val userId: Long,
    val dateKey: String,
    val period: String,
    val zoneId: String,
    val mode: String,
    val input: String,
    val baseQuantity: Long?,
    val baseRevision: Long,
    val updatedAtMillis: Long
) {
    fun toDomain() = SandhyaCountDraft(id, userId, dateKey, SandhyaPeriod.valueOf(period), zoneId,
        SandhyaCountMode.valueOf(mode), input, baseQuantity, baseRevision, updatedAtMillis)

    companion object {
        fun from(draft: SandhyaCountDraft) = with(draft) {
            SandhyaCountDraftEntity(id, userId, dateKey, period.name, zoneId, mode.name, input,
                baseQuantity, baseRevision, updatedAtMillis)
        }
    }
}
