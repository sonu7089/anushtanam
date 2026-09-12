package com.mantrasiddhi.data.local.entity

import androidx.room.Entity

/** Coalesced record identities, committed by SQLite with the source mutation. */
@Entity(tableName = "changed_records", primaryKeys = ["userId", "kind", "recordId"])
data class ChangedRecordEntity(val userId: Long, val kind: String, val recordId: String)
