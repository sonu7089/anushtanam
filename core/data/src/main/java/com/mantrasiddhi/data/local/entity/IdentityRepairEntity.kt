package com.mantrasiddhi.data.local.entity

import androidx.room.Entity

/** Original rows retained by v15 repair. Never uploaded or included in performed totals. */
@Entity(tableName = "identity_repair_archive", primaryKeys = ["tableName", "rowId"])
data class IdentityRepairEntity(
    val tableName: String,
    val rowId: String,
    val userId: Long,
    val payload: String,
    val reason: String
)
