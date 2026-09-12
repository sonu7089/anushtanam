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
        com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("backup.encodes")
        val encoded = BackupFingerprint.encode(payload)
        val previous = database.backupDao().queued(uid, key)
        val versionKey = "fingerprint:v1:$key"
        val canonical = database.backupDao().queued(uid, versionKey) == "1"
        if (previous != null && !canonical) com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("backup.legacyNormalizations")
        if (previous != null && (if (canonical) previous else BackupFingerprint.encode(CloudSyncPolicy.decode(previous))) == encoded) {
            if (!canonical) {
                database.backupDao().putQueued(BackupQueueState(uid, key, encoded))
                database.backupDao().putQueued(BackupQueueState(uid, versionKey, "1"))
            }
            return
        }
        outbox.enqueueInTransaction(uid, kind, payload)
        database.backupDao().putQueued(BackupQueueState(uid, key, encoded))
        database.backupDao().putQueued(BackupQueueState(uid, versionKey, "1"))
    }

    /** The seed and marker commit together; subsequent captures only drain durable IDs. */
    suspend fun capture(userId: Long, ownerStillActive: () -> Boolean) {
        val uid = database.userDao().getById(userId)?.firebaseUid ?: return
        database.withTransaction {
            check(ownerStillActive())
            val dao = database.backupDao()
            val key = "capture:v1:$userId"
            if (dao.queued(uid, key) != "1") {
                com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("backup.bootstrap")
                database.japaDao().project(userId, System.currentTimeMillis())
                dao.seedEntries(userId); dao.seedTargets(userId); dao.seedVows(userId)
                dao.seedSessions(userId); dao.seedLogs(userId); dao.seedChanges(userId)
                dao.putQueued(BackupQueueState(uid, key, "1"))
            } else database.japaDao().project(userId, System.currentTimeMillis(), repair = false)
            database.sandhyaCountDao().rebuildPeriods(userId, recordHistory = false)
        }
        do {
            val more = database.withTransaction {
                check(ownerStillActive())
                drainPage(userId, uid)
            }
        } while (more)
    }

    /** Caller owns the original write transaction. A failed encode rolls back the save too. */
    suspend fun drain(userId: Long) {
        val uid = database.userDao().getById(userId)?.firebaseUid ?: return
        while (drainPage(userId, uid)) { /* bounded pages, one atomic caller transaction */ }
    }

    private suspend fun drainPage(userId: Long, uid: String): Boolean {
        val dao = database.backupDao()
        val page = dao.changed(userId)
        for (event in page) {
            com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("backup.changedRecords")
            when (event.kind) {
                "entry" -> database.japaDao().entry(event.recordId)?.takeIf { it.userId == userId }?.let { entry ->
                    val session = entry.sessionId?.let { dao.sessionByLocalId(it) }
                    enqueue(uid, SyncKind.JAPA_ENTRY, entry.payload() + ("sessionStableId" to (session?.stableId ?: entry.sessionStableId)))
                }
                "target" -> dao.targetById(userId, event.recordId.toLong())?.takeIf { it.userId == it.setByUserId }?.let { target ->
                    enqueue(uid, SyncKind.JAPA_GOAL, mapOf(
                        "id" to target.stableId, "kind" to "target", "mantraId" to target.mantraId,
                        "startedAtMillis" to target.startedAtMillis, "endedAtMillis" to target.endedAtMillis,
                        "baselineCount" to target.baselineCount, "targetCount" to target.targetRecitationCount,
                        "rewardType" to target.rewardType, "rewardDescription" to target.rewardDescription))
                }
                "vow" -> dao.vowById(userId, event.recordId.toLong())?.let { vow ->
                    enqueue(uid, SyncKind.JAPA_GOAL, mapOf(
                        "id" to vow.stableId, "kind" to "vow", "mantraId" to vow.mantraId,
                        "startedAtMillis" to vow.startedAtMillis, "endedAtMillis" to vow.endedAtMillis,
                        "baselineCount" to vow.baselineCount, "targetCount" to vow.targetJapaCount,
                        "aksharas" to vow.mantraAksharaCount, "completedAtMillis" to vow.completedAtMillis))
                }
                "session" -> dao.sessionByLocalId(event.recordId.toLong())?.takeIf { it.userId == userId }?.let { row ->
                    enqueue(uid, SyncKind.PRACTICE_RECORD, PracticeRecordCodec.session(row, row.acceptedEntryId))
                }
                "log" -> dao.logById(userId, event.recordId.toLong())?.let {
                    enqueue(uid, SyncKind.PRACTICE_RECORD, PracticeRecordCodec.log(it))
                }
                "change" -> database.sandhyaCountDao().getChange(event.recordId)?.takeIf { it.userId == userId }?.let {
                    enqueue(uid, SyncKind.PRACTICE_RECORD, PracticeRecordCodec.change(it))
                }
            }
            dao.clearChanged(event)
        }
        return page.size == 200
    }

    suspend fun merge(userId: Long, payload: Map<String, Any?>) {
        val dao = database.backupDao()
        when (payload.text("type")) {
            "session" -> {
                val old = dao.session(userId, payload.text("id"))
                require(old == null || old == PracticeRecordCodec.session(payload, userId, old.id).copy(acceptedEntryId = old.acceptedEntryId)) { "Saved assessments are immutable" }
                val claimedEntry = payload["entryId"] as? String
                require(old?.acceptedEntryId == null || claimedEntry == null || old.acceptedEntryId == claimedEntry) { "Session entry identity is immutable" }
                val id = old?.id ?: database.recitationDao().insert(PracticeRecordCodec.session(payload, userId, 0))
                if (claimedEntry != null) dao.linkSessionEntry(id, claimedEntry)
                val entry = database.japaDao().entry(payload.text("entryId"))
                    ?: database.japaDao().audioEntryByStableId(userId, payload.text("id"))
                if (entry != null) {
                    check(entry.userId == userId)
                    database.japaDao().putEntry(entry.copy(sessionId = id, sessionStableId = payload.text("id")))
                }
            }
            "sandhya" -> {
                val old = dao.log(userId, payload.text("dateKey"), payload.text("period"))
                val merged = if (old == null) payload else PracticeRecordCodec.mergeLog(PracticeRecordCodec.log(old), payload)
                val row = PracticeRecordCodec.log(merged, userId, old?.id ?: 0)
                if (row != old) dao.putLog(row)
            }
            "change" -> dao.putChange(PracticeRecordCodec.change(payload, userId))
            else -> error("Unsupported practice record")
        }
        val uid = requireNotNull(database.userDao().getById(userId)?.firebaseUid)
        val key = "${SyncKind.PRACTICE_RECORD}:${payload.text("type")}:${payload.text("id")}"
        // Prevent a restored record from being uploaded again as new practice.
        val encoded = BackupFingerprint.encode(payload)
        if (dao.queued(uid, key) != encoded) dao.putQueued(BackupQueueState(uid, key, encoded))
        if (dao.queued(uid, "fingerprint:v1:$key") != "1") dao.putQueued(BackupQueueState(uid, "fingerprint:v1:$key", "1"))
    }
}
