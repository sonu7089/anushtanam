package com.mantrasiddhi.data.sync

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
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
    private val outbox: CloudSyncOutbox
) {
    private val restoreLock = Mutex()

    /** Called inside the same Room transaction as the practice or goal change. */
    suspend fun enqueue(userId: Long, before: List<JapaEntryEntity> = emptyList()) {
        val uid = database.userDao().getById(userId)?.firebaseUid ?: return
        val dao = database.japaDao()
        val previous = before.associateBy { it.id }
        for (entry in dao.entries(userId)) if (entry != previous[entry.id]) {
            outbox.enqueueInTransaction(uid, SyncKind.JAPA_ENTRY, entry.payload())
        }
        for (target in dao.targets(userId).filter { it.userId == it.setByUserId }) {
            outbox.enqueueInTransaction(uid, SyncKind.JAPA_GOAL, mapOf(
                "id" to target.stableId, "kind" to "target", "mantraId" to target.mantraId,
                "startedAtMillis" to target.startedAtMillis, "endedAtMillis" to target.endedAtMillis,
                "baselineCount" to target.baselineCount, "targetCount" to target.targetRecitationCount,
                "rewardType" to target.rewardType, "rewardDescription" to target.rewardDescription))
        }
        for (vow in dao.vows(userId)) {
            outbox.enqueueInTransaction(uid, SyncKind.JAPA_GOAL, mapOf(
                "id" to vow.stableId, "kind" to "vow", "mantraId" to vow.mantraId,
                "startedAtMillis" to vow.startedAtMillis, "endedAtMillis" to vow.endedAtMillis,
                "baselineCount" to vow.baselineCount, "targetCount" to vow.targetJapaCount,
                "aksharas" to vow.mantraAksharaCount))
        }
    }

    suspend fun schedule() = outbox.schedule()

    suspend fun importOwnedProfile(userId: Long) {
        database.withTransaction { enqueue(userId) }
        schedule()
    }

    suspend fun deliver(uid: String, kind: String, payload: Map<String, Any?>) {
        check(auth.currentUser?.uid == uid)
        val collection = if (kind == SyncKind.JAPA_ENTRY) "japaEntries" else "japaGoals"
        val ref = firestore.collection("users").document(uid).collection(collection).document(payload.getValue("id").toString())
        firestore.runTransaction { transaction ->
            check(auth.currentUser?.uid == uid)
            val old = transaction.get(ref).data
            val legacyBaseline = if (kind == SyncKind.JAPA_GOAL && old == null && payload.text("id").startsWith("legacy-")) {
                val legacyCollection = if (payload.text("kind") == "target") "selfTargets" else "purashcharana"
                val mirror = transaction.get(firestore.collection(legacyCollection).document(uid)).data
                JapaSyncPolicy.legacyBaseline(payload, mirror)
            } else payload.number("baselineCount")
            if (kind == SyncKind.JAPA_ENTRY) {
                if (JapaSyncPolicy.newer(payload, old)) {
                    transaction.set(ref, payload)
                    transaction.set(ref.collection("revisions").document(payload.text("operationId")), payload)
                }
            } else {
                // A baseline is established once. A late snapshot cannot reopen a withdrawn goal.
                val merged = if (old == null) payload + ("baselineCount" to legacyBaseline) else old + mapOf(
                    "endedAtMillis" to listOfNotNull(old.numberOrNull("endedAtMillis"), payload.numberOrNull("endedAtMillis")).minOrNull())
                transaction.set(ref, merged)
            }
            Unit
        }.awaitResult()
    }

    /** Pagination bounds each network response. Merge and period rebuilding commit together. */
    suspend fun restore(uid: String) = restoreLock.withLock {
        check(auth.currentUser?.uid == uid)
        val user = database.userDao().getByFirebaseUid(uid) ?: return@withLock
        suspend fun read(collection: String): List<Map<String, Any?>> {
            val result = mutableListOf<Map<String, Any?>>()
            var query: Query = firestore.collection("users").document(uid).collection(collection)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(200)
            while (true) {
                check(auth.currentUser?.uid == uid)
                val page = query.get(Source.SERVER).awaitResult().documents
                result.addAll(page.mapNotNull { it.data })
                if (page.size < 200) break
                query = query.startAfter(page.last())
            }
            return result
        }
        val goals = read("japaGoals")
        val entries = read("japaEntries")
        val repaired = JapaReplicaMerger(database).merge(user.id, goals, entries) { auth.currentUser?.uid == uid }
        if (repaired) {
            database.withTransaction {
                check(auth.currentUser?.uid == uid)
                enqueue(user.id)
            }
            schedule()
        }
    }
}

internal fun JapaEntryEntity.payload(): Map<String, Any?> = mapOf(
    "id" to id, "mantraId" to mantraId, "dateKey" to dateKey, "zoneId" to zoneId, "period" to period,
    "quantity" to quantity, "revision" to revision, "createdAtMillis" to createdAtMillis,
    "targetId" to targetId, "vowId" to vowId, "source" to source, "updatedAtMillis" to updatedAtMillis, "operationId" to operationId)
internal fun Map<String, Any?>.text(key: String) = this[key] as? String ?: ""
internal fun Map<String, Any?>.number(key: String) = numberOrNull(key) ?: 0
internal fun Map<String, Any?>.numberOrNull(key: String) = (this[key] as? Number)?.toLong()
