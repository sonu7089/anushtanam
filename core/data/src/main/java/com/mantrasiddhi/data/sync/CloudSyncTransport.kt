package com.mantrasiddhi.data.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.Timestamp
import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import com.mantrasiddhi.data.remote.FirebaseContract as F
import com.mantrasiddhi.data.remote.awaitResult
import com.mantrasiddhi.data.sync.CloudSyncPolicy.long
import javax.inject.Inject

/** A minute's changes and their retry checkpoints commit in one server transaction. */
class CloudSyncTransport @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    suspend fun deliver(events: List<CloudSyncEntity>) {
        if (events.isEmpty()) return
        val uid = events.first().uid
        require(events.all { it.uid == uid } && events.zipWithNext().all { (a, b) -> a.id < b.id })
        check(auth.currentUser?.uid == uid)
        val payloads = events.associate { it.id to CloudSyncPolicy.decode(it.payload) }
        fun document(collection: String) = firestore.collection(collection).document(uid)
        val account = document(F.COLLECTION_USERS)
        val progress = document(F.COLLECTION_PROGRESS)
        val target = document(F.COLLECTION_SELF_TARGETS)
        val sankalpa = document(F.COLLECTION_PURASHCHARANA)
        val kinds = events.map { it.kind }.toSet()
        // Fetch only unfinished goals; checkpoints prevent replay from redirecting
        // old japa to a later goal after the original one has completed.
        val goals = if (SyncKind.CHILD_GOAL in kinds) {
            firestore.collection(F.COLLECTION_GOALS).whereEqualTo(F.FIELD_CHILD_UID, uid)
                .whereEqualTo(F.FIELD_IS_COMPLETED, false)
                .get(Source.SERVER).awaitResult().documents.map { it.reference }
        } else emptyList()
        val references = buildSet {
            add(account)
            if (SyncKind.PROGRESS in kinds) add(progress)
            if (kinds.any { it in setOf(SyncKind.TARGET_CREATE, SyncKind.TARGET_INCREMENT, SyncKind.TARGET_CLEAR) }) add(target)
            if (SyncKind.PURASHCHARANA in kinds) add(sankalpa)
            addAll(goals)
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
                    SyncKind.PROGRESS -> {
                        val remote = state[progress].orEmpty()
                        @Suppress("UNCHECKED_CAST")
                        val markers = (remote[CloudSyncPolicy.CHECKPOINTS] as? Map<String, Any?>).orEmpty()
                        state[progress] = CloudSyncPolicy.progress(remote, payload) +
                            (CloudSyncPolicy.CHECKPOINTS to (markers + (event.streamId to event.id)))
                        touched += progress
                    }
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
                    SyncKind.CHILD_GOAL -> {
                        val active = goals.filter { ref ->
                            val data = state[ref].orEmpty()
                            data[F.FIELD_CHILD_UID] == uid && data[F.FIELD_IS_COMPLETED] != true &&
                                (data[F.FIELD_CREATED_AT] as? Timestamp)?.toDate()?.time?.let { it <= event.createdAtMs } == true
                        }.minByOrNull { (state[it]?.get(F.FIELD_CREATED_AT) as Timestamp).toDate().time }
                        if (active != null) {
                            val remote = state[active].orEmpty()
                            val count = remote.long(F.FIELD_CURRENT_COUNT) + 1
                            state[active] = remote + mapOf(F.FIELD_CURRENT_COUNT to count,
                                F.FIELD_IS_COMPLETED to (count >= remote.long(F.FIELD_TARGET_COUNT)))
                            touched += active
                        }
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
