package com.mantrasiddhi.data.local.entity

import androidx.room.Entity

/** Local checkpoints never imply success until the server has acknowledged the work. */
@Entity(tableName = "backup_queue_state", primaryKeys = ["uid", "key"])
data class BackupQueueState(val uid: String, val key: String, val payload: String)

@Entity(tableName = "backup_state", primaryKeys = ["uid"])
data class BackupStateEntity(
    val uid: String,
    val lastSuccessMs: Long = 0,
    val needsAttention: Boolean = false,
    val restorePending: Boolean = true,
    val bootstrapped: Boolean = false,
    val cursorSeconds: Long = 0,
    val cursorNanos: Int = 0
)

@Entity(tableName = "backup_conflicts", primaryKeys = ["uid", "id"])
data class BackupConflictEntity(
    val uid: String, val id: String, val entryId: String,
    val firstPayload: String, val secondPayload: String,
    val resolved: Boolean = false
)
