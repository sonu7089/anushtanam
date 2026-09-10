package com.mantrasiddhi.data.sync

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.entity.*
import com.mantrasiddhi.data.remote.awaitResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Entries and goal identities sync separately from the old, non-editable audio summaries. */
@Singleton
class JapaCloudSync @Inject constructor(
    private val database: MantraSiddhiDatabase,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val outbox: CloudSyncOutbox,
    private val backup: PracticeBackupStore
) {
    private val restoreLock = Mutex()

    /** Called inside the same Room transaction as the practice or goal change. */
    suspend fun enqueue(userId: Long, before: List<JapaEntryEntity> = emptyList()) {
        val uid = database.userDao().getById(userId)?.firebaseUid ?: return
        val dao = database.japaDao()
        val previous = before.associateBy { it.id }
        for (entry in dao.entries(userId)) if (entry != previous[entry.id]) {
            val session = entry.sessionId?.let { database.backupDao().sessionByLocalId(it) }
            backup.enqueue(uid, SyncKind.JAPA_ENTRY, entry.payload() + ("sessionStableId" to session?.stableId))
        }
        for (target in dao.targets(userId).filter { it.userId == it.setByUserId }) {
            backup.enqueue(uid, SyncKind.JAPA_GOAL, mapOf(
                "id" to target.stableId, "kind" to "target", "mantraId" to target.mantraId,
                "startedAtMillis" to target.startedAtMillis, "endedAtMillis" to target.endedAtMillis,
                "baselineCount" to target.baselineCount, "targetCount" to target.targetRecitationCount,
                "rewardType" to target.rewardType, "rewardDescription" to target.rewardDescription))
        }
        for (vow in dao.vows(userId)) {
            backup.enqueue(uid, SyncKind.JAPA_GOAL, mapOf(
                "id" to vow.stableId, "kind" to "vow", "mantraId" to vow.mantraId,
                "startedAtMillis" to vow.startedAtMillis, "endedAtMillis" to vow.endedAtMillis,
                "baselineCount" to vow.baselineCount, "targetCount" to vow.targetJapaCount,
                "aksharas" to vow.mantraAksharaCount, "completedAtMillis" to vow.completedAtMillis))
        }
    }

    suspend fun schedule() = outbox.schedule()

    /** Performance and its upload are saved in the same transaction as the recitation. */
    suspend fun enqueueSession(userId: Long, sessionId: Long) {
        val uid = database.userDao().getById(userId)?.firebaseUid ?: return
        val row = database.backupDao().sessionByLocalId(sessionId) ?: return
        check(row.userId == userId)
        val entry = database.japaDao().audioEntry(userId, sessionId)
        backup.enqueue(uid, SyncKind.PRACTICE_RECORD, PracticeRecordCodec.session(row, entry?.id))
    }

    /** Signing in on a fresh installation needs recovery; an already synced one stays idle. */
    suspend fun requestRecovery(uid: String) {
        database.withTransaction {
            check(auth.currentUser?.uid == uid)
            if (database.backupDao().state(uid) == null) database.backupDao().putState(BackupStateEntity(uid))
        }
        if (needsRestore(uid)) schedule()
    }

    suspend fun importOwnedProfile(userId: Long) {
        database.withTransaction { enqueue(userId) }
        val uid = database.userDao().getById(userId)?.firebaseUid ?: return
        backup.capture(userId) { auth.currentUser?.uid == uid }
        schedule()
    }

    suspend fun capture(uid: String) {
        val user = database.userDao().getByFirebaseUid(uid) ?: return
        database.withTransaction {
            check(auth.currentUser?.uid == uid)
            enqueue(user.id)
        }
        backup.capture(user.id) { auth.currentUser?.uid == uid }
        if (database.cloudSyncDao().hasPending(uid)) schedule()
    }

    suspend fun needsRestore(uid: String): Boolean = database.backupDao().state(uid)?.restorePending != false

    suspend fun markRestorePending(uid: String) = updateState(uid) { it.copy(restorePending = true) }

    suspend fun markFailure(uid: String) = updateState(uid) { it.copy(needsAttention = true, restorePending = true) }

    private suspend fun updateState(uid: String, update: (BackupStateEntity) -> BackupStateEntity) {
        database.withTransaction {
            database.backupDao().putState(update(database.backupDao().state(uid) ?: BackupStateEntity(uid)))
        }
    }

    suspend fun deliver(uid: String, kind: String, payload: Map<String, Any?>) {
        check(auth.currentUser?.uid == uid)
        val owner = firestore.collection("users").document(uid)
        val collection = when (kind) {
            SyncKind.JAPA_ENTRY -> "japaEntries"
            SyncKind.JAPA_GOAL -> "japaGoals"
            SyncKind.PRACTICE_RECORD -> "practiceRecords"
            SyncKind.CONFLICT_RESOLUTION -> "japaConflicts"
            else -> error("Unsupported backup operation")
        }
        val documentId = if (kind == SyncKind.PRACTICE_RECORD) "${payload.text("type")}_${payload.text("id")}" else payload.text("id")
        val ref = owner.collection(collection).document(documentId)
        firestore.runTransaction { transaction ->
            check(auth.currentUser?.uid == uid)
            val old = transaction.get(ref).data
            val revisionRef = if (kind == SyncKind.JAPA_ENTRY) ref.collection("revisions").document(payload.text("operationId")) else null
            val revisionExists = revisionRef?.let { transaction.get(it).exists() } == true
            val baseline = if (kind == SyncKind.JAPA_GOAL && old == null && payload.text("id").startsWith("legacy-")) {
                val mirrorCollection = if (payload.text("kind") == "target") "selfTargets" else "purashcharana"
                JapaSyncPolicy.legacyBaseline(payload, transaction.get(firestore.collection(mirrorCollection).document(uid)).data)
            } else payload.number("baselineCount")
            val conflictId = if (kind == SyncKind.JAPA_ENTRY && old != null && !revisionExists &&
                old.text("operationId") != payload.text("operationId") && old.number("revision") >= payload.number("revision") &&
                old.number("quantity") != payload.number("quantity")) {
                documentId + ":" + listOf(old.text("operationId"), payload.text("operationId")).sorted().joinToString(":")
            } else null
            val conflictRef = conflictId?.let { owner.collection("japaConflicts").document(it) }
            val existingConflict = conflictRef?.let { transaction.get(it).data }
            // All reads above precede all writes below, including conflict history.
            fun publish(type: String, id: String, value: Map<String, Any?>) {
                transaction.set(owner.collection("backupChanges").document("${type}_$id"), mapOf(
                    "type" to type, "id" to id, "value" to value, "changedAt" to FieldValue.serverTimestamp()))
            }
            if (conflictRef != null && existingConflict == null) {
                val conflict = mapOf("id" to conflictId, "entryId" to documentId,
                    "first" to old, "second" to payload, "resolved" to false)
                transaction.set(conflictRef, conflict)
                publish("conflict", conflictId!!, conflict)
            }
            when (kind) {
                SyncKind.JAPA_ENTRY -> {
                    // Keep rejected revisions too, so a competing edit is never lost.
                    if (revisionRef != null && !revisionExists) transaction.set(revisionRef, payload)
                    if (JapaSyncPolicy.newer(payload, old)) {
                        transaction.set(ref, payload)
                        publish("entry", documentId, payload)
                    }
                }
                SyncKind.JAPA_GOAL -> {
                    val merged = if (old == null) payload + ("baselineCount" to baseline) else old + mapOf(
                        "endedAtMillis" to listOfNotNull(old.numberOrNull("endedAtMillis"), payload.numberOrNull("endedAtMillis")).minOrNull()) +
                        (if (payload.text("kind") == "vow") mapOf("completedAtMillis" to listOfNotNull(
                            old.numberOrNull("completedAtMillis"), payload.numberOrNull("completedAtMillis")).maxOrNull()) else emptyMap())
                    if (merged != old) { transaction.set(ref, merged); publish("goal", documentId, merged) }
                }
                SyncKind.PRACTICE_RECORD -> {
                    val merged = if (old != null && payload.text("type") == "sandhya") PracticeRecordCodec.mergeLog(old, payload) else old ?: payload
                    if (merged != old) { transaction.set(ref, merged); publish("practice", documentId, merged) }
                }
                SyncKind.CONFLICT_RESOLUTION -> if (old != null && old["resolved"] != true) {
                    val merged = old + ("resolved" to true)
                    transaction.set(ref, merged)
                    publish("conflict", documentId, merged)
                }
            }
            Unit
        }.awaitResult()
    }

    /** Each page and checkpoint commit together. Replayed pages are harmless. */
    suspend fun restore(uid: String) = restoreLock.withLock {
        check(auth.currentUser?.uid == uid)
        val user = database.userDao().getByFirebaseUid(uid) ?: return@withLock
        val owner = firestore.collection("users").document(uid)
        val merger = JapaReplicaMerger(database)
        markRestorePending(uid)
        if (database.backupDao().state(uid)?.bootstrapped != true) {
            // One compatibility pass for records written before the change feed existed.
            for (collection in listOf("japaGoals", "japaEntries")) {
                var query: Query = owner.collection(collection).orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(200)
                database.backupDao().queued(uid, "bootstrap:$collection")?.let { query = query.startAfter(it) }
                while (true) {
                    check(auth.currentUser?.uid == uid)
                    val page = query.get(Source.SERVER).awaitResult().documents
                    val values = page.mapNotNull { it.data }
                    database.withTransaction {
                        merger.merge(user.id, if (collection == "japaGoals") values else emptyList(),
                            if (collection == "japaEntries") values else emptyList(), { auth.currentUser?.uid == uid }, project = false)
                        page.lastOrNull()?.let { database.backupDao().putQueued(BackupQueueState(uid, "bootstrap:$collection", it.id)) }
                    }
                    if (page.size < 200) break
                    query = query.startAfter(page.last())
                }
            }
            updateState(uid) { it.copy(bootstrapped = true) }
        }
        val state = database.backupDao().state(uid) ?: BackupStateEntity(uid)
        // Inclusive time boundary handles documents sharing the same server timestamp.
        var query: Query = owner.collection("backupChanges")
            .whereGreaterThanOrEqualTo("changedAt", Timestamp(state.cursorSeconds, state.cursorNanos))
            .orderBy("changedAt").orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(200)
        while (true) {
            check(auth.currentUser?.uid == uid)
            val page = query.get(Source.SERVER).awaitResult().documents
            database.withTransaction {
                check(auth.currentUser?.uid == uid)
                for (doc in page) {
                    @Suppress("UNCHECKED_CAST")
                    val value = doc.get("value") as? Map<String, Any?> ?: error("Missing backup record")
                    when (doc.getString("type")) {
                        "entry" -> merger.merge(user.id, emptyList(), listOf(value), { auth.currentUser?.uid == uid }, project = false)
                        "goal" -> merger.merge(user.id, listOf(value), emptyList(), { auth.currentUser?.uid == uid }, project = false)
                        "practice" -> backup.merge(user.id, value)
                        "conflict" -> {
                            val id = value.text("id")
                            val previous = database.backupDao().conflict(uid, id)
                            @Suppress("UNCHECKED_CAST")
                            val first = value["first"] as Map<String, Any?>
                            @Suppress("UNCHECKED_CAST")
                            val second = value["second"] as Map<String, Any?>
                            database.backupDao().putConflict(BackupConflictEntity(uid, id, value.text("entryId"),
                                org.json.JSONObject(first).toString(), org.json.JSONObject(second).toString(),
                                previous?.resolved == true || value["resolved"] == true))
                        }
                        else -> error("Unsupported backup version")
                    }
                }
                val timestamp = page.lastOrNull()?.getTimestamp("changedAt")
                if (timestamp != null) updateState(uid) { it.copy(cursorSeconds = timestamp.seconds, cursorNanos = timestamp.nanoseconds) }
            }
            if (page.size < 200) break
            query = query.startAfter(page.last())
        }
        database.withTransaction {
            check(auth.currentUser?.uid == uid)
            merger.merge(user.id, emptyList(), emptyList(), { auth.currentUser?.uid == uid })
            enqueue(user.id)
            val pending = database.cloudSyncDao().hasPending(uid)
            updateState(uid) { it.copy(restorePending = false, needsAttention = false,
                lastSuccessMs = if (pending) it.lastSuccessMs else System.currentTimeMillis()) }
        }
        if (database.cloudSyncDao().hasPending(uid)) schedule()
    }

    suspend fun resolveConflict(uid: String, id: String, useAlternative: Boolean, expectedOperation: String) {
        database.withTransaction {
            check(auth.currentUser?.uid == uid)
            val conflict = database.backupDao().conflict(uid, id) ?: return@withTransaction
            if (conflict.resolved) return@withTransaction
            val user = database.userDao().getByFirebaseUid(uid) ?: return@withTransaction
            val entry = database.japaDao().entry(conflict.entryId) ?: error("Practice is still restoring")
            check(entry.userId == user.id && entry.operationId == expectedOperation)
            run {
                val first = CloudSyncPolicy.decode(conflict.firstPayload)
                val second = CloudSyncPolicy.decode(conflict.secondPayload)
                val alternative = if (JapaSyncPolicy.newer(first, second)) second else first
                database.japaDao().putEntry(entry.copy(quantity = if (useAlternative) alternative.number("quantity") else entry.quantity,
                    revision = Math.addExact(entry.revision, 1), updatedAtMillis = System.currentTimeMillis(),
                    operationId = java.util.UUID.randomUUID().toString()))
                database.sandhyaCountDao().rebuildPeriods(user.id)
                database.japaDao().project(user.id, System.currentTimeMillis())
                enqueue(user.id)
            }
            database.backupDao().putConflict(conflict.copy(resolved = true))
            backup.enqueue(uid, SyncKind.CONFLICT_RESOLUTION, mapOf("id" to id))
        }
        schedule()
    }

}

internal fun JapaEntryEntity.payload(): Map<String, Any?> = mapOf(
    "id" to id, "mantraId" to mantraId, "dateKey" to dateKey, "zoneId" to zoneId, "period" to period,
    "quantity" to quantity, "revision" to revision, "createdAtMillis" to createdAtMillis,
    "targetId" to targetId, "vowId" to vowId, "source" to source, "updatedAtMillis" to updatedAtMillis, "operationId" to operationId)
internal fun Map<String, Any?>.text(key: String) = this[key] as? String ?: ""
internal fun Map<String, Any?>.number(key: String) = numberOrNull(key) ?: 0
internal fun Map<String, Any?>.numberOrNull(key: String) = (this[key] as? Number)?.toLong()
