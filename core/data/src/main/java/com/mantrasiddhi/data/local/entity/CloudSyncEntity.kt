package com.mantrasiddhi.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Durable operations, ordered per Firebase account. Payloads contain compact aggregates only. */
@Entity(tableName = "cloud_sync_outbox", indices = [Index(value = ["uid", "id"])])
data class CloudSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val streamId: String,
    val kind: String,
    val payload: String,
    val createdAtMs: Long
)

@Entity(tableName = "cloud_sync_schedule")
data class CloudSyncScheduleEntity(@PrimaryKey val uid: String, val nextAttemptAtMs: Long)
