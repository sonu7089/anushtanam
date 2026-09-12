package com.mantrasiddhi.data.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import com.mantrasiddhi.data.remote.FirebaseContract as F
import com.mantrasiddhi.data.remote.awaitResult
import com.mantrasiddhi.data.sync.CloudSyncPolicy.long
import javax.inject.Inject

/** A minute's changes and their retry checkpoints commit in one server transaction. */
class CloudSyncTransport @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val japaSync: JapaCloudSync
) {
    suspend fun deliver(events: List<CloudSyncEntity>) {
        if (events.isEmpty()) return
        val uid = events.first().uid
        require(events.all { it.uid == uid } && events.zipWithNext().all { (a, b) -> a.id < b.id })
        check(auth.currentUser?.uid == uid)
        val japaEvents = events.filter { it.kind in setOf(SyncKind.JAPA_ENTRY, SyncKind.JAPA_GOAL,
            SyncKind.PRACTICE_RECORD, SyncKind.CONFLICT_RESOLUTION) }
        // Anonymous historical deltas cannot be safely attributed. Canonical bootstrap
        // repairs them instead of risking a second increment beside session delivery.
        val legacy = events.filterNot { it in japaEvents || it.kind in setOf(SyncKind.PROGRESS, SyncKind.CHILD_GOAL) }
        if (legacy.isNotEmpty()) deliverLegacy(legacy)
        // Complete pre-cutover writes before establishing the legacy baseline once.
        for (event in japaEvents) {
            val payload = CloudSyncPolicy.decode(event.payload)
            if (event.kind == SyncKind.PRACTICE_RECORD && payload["type"] == "session")
                japaSync.deliverSession(uid, payload, event)
            else japaSync.deliver(uid, event.kind, payload)
        }
    }

    private suspend fun deliverLegacy(events: List<CloudSyncEntity>) {
        val uid = events.first().uid
        val payloads = events.associate { it.id to CloudSyncPolicy.decode(it.payload) }
        fun document(collection: String) = firestore.collection(collection).document(uid)
        val account = document(F.COLLECTION_USERS)
        val target = document(F.COLLECTION_SELF_TARGETS)
        val sankalpa = document(F.COLLECTION_PURASHCHARANA)
        val kinds = events.map { it.kind }.toSet()
        val references = buildSet {
            add(account)
            if (kinds.any { it in setOf(SyncKind.TARGET_CREATE, SyncKind.TARGET_INCREMENT, SyncKind.TARGET_CLEAR) }) add(target)
            if (SyncKind.PURASHCHARANA in kinds) add(sankalpa)
        }
        firestore.runTransaction { transaction ->
            check(auth.currentUser?.uid == uid)
            // Firestore requires all reads before any writes.
            val state: MutableMap<DocumentReference, Map<String, Any?>?> =
                references.associateWith { transaction.get(it).data }.toMutableMap()
            @Suppress("UNCHECKED_CAST")
            val originalCheckpoints = (state[account]?.get(CloudSyncPolicy.CHECKPOINTS) as? Map<String, Any?>).orEmpty()
            val checkpoints = originalCheckpoints.toMutableMap()
            val touched = mutableSetOf<DocumentReference>()
            for (event in events) {
                if (CloudSyncPolicy.acknowledged(checkpoints, event)) continue
                val payload = payloads.getValue(event.id)
                when (event.kind) {
                    SyncKind.TARGET_CREATE -> { state[target] = payload; touched += target }
                    SyncKind.TARGET_CLEAR -> { state[target] = null; touched += target }
                    SyncKind.TARGET_INCREMENT -> {
                        val remote = state[target]
                        if (remote != null && remote[F.FIELD_IS_COMPLETED] != true) {
                            val count = remote.long(F.FIELD_CURRENT_COUNT) + 1
                            state[target] = remote + mapOf(F.FIELD_CURRENT_COUNT to count,
                                F.FIELD_IS_COMPLETED to (count >= remote.long(F.FIELD_TARGET_COUNT)))
                            touched += target
                        }
                    }
                    SyncKind.PURASHCHARANA -> {
                        state[sankalpa] = CloudSyncPolicy.purashcharana(state[sankalpa].orEmpty(), payload)
                        touched += sankalpa
                    }
                    else -> error("Unknown deferred sync operation: ${event.kind}")
                }
                checkpoints[event.streamId] = event.id
            }
            for (doc in touched) {
                val value = state[doc]
                if (value == null) transaction.delete(doc)
                else transaction.set(doc, value + (F.FIELD_UPDATED_AT to FieldValue.serverTimestamp()))
            }
            if (events.any { !CloudSyncPolicy.acknowledged(originalCheckpoints, it) }) {
                transaction.set(account, mapOf(CloudSyncPolicy.CHECKPOINTS to checkpoints), SetOptions.merge())
            }
            Unit
        }.awaitResult()
    }
}
