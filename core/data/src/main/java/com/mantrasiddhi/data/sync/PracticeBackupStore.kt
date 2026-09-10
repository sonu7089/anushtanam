package com.mantrasiddhi.data.sync

import androidx.room.withTransaction
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.entity.*
import javax.inject.Inject

class PracticeBackupStore @Inject constructor(
    private val database: MantraSiddhiDatabase,
    private val outbox: CloudSyncOutbox
) {
    /** Caller owns the Room transaction, including the original practice save. */
    suspend fun enqueue(uid: String, kind: String, payload: Map<String, Any?>) {
        val key = "$kind:${payload.text("type")}:${payload.text("id")}"
        val encoded = BackupFingerprint.encode(payload)
        val previous = database.backupDao().queued(uid, key)
        if (previous != null && BackupFingerprint.encode(CloudSyncPolicy.decode(previous)) == encoded) return
        outbox.enqueueInTransaction(uid, kind, payload)
        database.backupDao().putQueued(BackupQueueState(uid, key, encoded))
    }

    suspend fun capture(userId: Long, ownerStillActive: () -> Boolean) {
        val uid = database.userDao().getById(userId)?.firebaseUid ?: return
        var after = 0L
        do {
            val page = database.backupDao().sessions(userId, after)
            database.withTransaction {
                check(ownerStillActive())
                for (row in page) {
                    val entry = database.japaDao().audioEntry(userId, row.id)
                    enqueue(uid, SyncKind.PRACTICE_RECORD, PracticeRecordCodec.session(row, entry?.id))
                }
            }
            after = page.lastOrNull()?.id ?: after
        } while (page.size == 200)
        after = 0
        do {
            val page = database.backupDao().logs(userId, after)
            database.withTransaction {
                check(ownerStillActive())
                for (row in page) enqueue(uid, SyncKind.PRACTICE_RECORD, PracticeRecordCodec.log(row))
            }
            after = page.lastOrNull()?.id ?: after
        } while (page.size == 200)
        var changeAfter = ""
        do {
            val page = database.backupDao().changes(userId, changeAfter)
            database.withTransaction {
                check(ownerStillActive())
                for (row in page) enqueue(uid, SyncKind.PRACTICE_RECORD, PracticeRecordCodec.change(row))
            }
            changeAfter = page.lastOrNull()?.id ?: changeAfter
        } while (page.size == 200)
    }

    suspend fun merge(userId: Long, payload: Map<String, Any?>) {
        val dao = database.backupDao()
        when (payload.text("type")) {
            "session" -> {
                val old = dao.session(userId, payload.text("id"))
                val id = old?.id ?: database.recitationDao().insert(PracticeRecordCodec.session(payload, userId, 0))
                val entry = database.japaDao().entry(payload.text("entryId"))
                if (entry != null) {
                    check(entry.userId == userId)
                    database.japaDao().putEntry(entry.copy(sessionId = id))
                }
            }
            "sandhya" -> {
                val old = dao.log(userId, payload.text("dateKey"), payload.text("period"))
                val merged = if (old == null) payload else PracticeRecordCodec.mergeLog(PracticeRecordCodec.log(old), payload)
                dao.putLog(PracticeRecordCodec.log(merged, userId, old?.id ?: 0))
            }
            "change" -> dao.putChange(PracticeRecordCodec.change(payload, userId))
            else -> error("Unsupported practice record")
        }
        val uid = requireNotNull(database.userDao().getById(userId)?.firebaseUid)
        val key = "${SyncKind.PRACTICE_RECORD}:${payload.text("type")}:${payload.text("id")}"
        // Prevent a restored record from being uploaded again as new practice.
        dao.putQueued(BackupQueueState(uid, key, BackupFingerprint.encode(payload)))
    }
}
