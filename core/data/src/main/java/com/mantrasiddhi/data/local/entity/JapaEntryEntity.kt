package com.mantrasiddhi.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A contribution belongs to its original goals for its entire revision history. */
@Entity(tableName = "japa_entries", indices = [Index(value = ["userId", "dateKey", "period"]),
    Index(value = ["targetId"]), Index(value = ["vowId"]),
    Index(value = ["userId", "sessionId"], unique = true),
    Index(value = ["userId", "sessionStableId"], unique = true)])
data class JapaEntryEntity(
    @PrimaryKey val id: String,
    val userId: Long,
    val mantraId: String,
    val dateKey: String,
    val zoneId: String,
    val period: String?,
    val quantity: Long,
    val revision: Long,
    val createdAtMillis: Long,
    val targetId: String?,
    val vowId: String?,
    val source: String,
    val sessionId: Long? = null,
    val updatedAtMillis: Long = createdAtMillis,
    val operationId: String = id,
    /** Retained even when the session has not arrived from backup yet. */
    val sessionStableId: String? = null
)
