package com.mantrasiddhi.data.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.mantrasiddhi.data.remote.FirebaseContract as F
import com.mantrasiddhi.data.remote.awaitResult
import com.mantrasiddhi.data.sync.CloudSyncPolicy.long
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A session's canonical creation, feed publication and every cloud count commit together.
 * Bootstrap scans canonical cloud history once per version, never a partial local seed.
 */
internal class SessionProjectionSync(private val cloud: FirebaseFirestore, private val auth: FirebaseAuth) {
    private val bootstrapLock = Mutex()
    private val readyAccounts = mutableSetOf<String>()
    private fun owner(uid: String) = cloud.collection("users").document(uid)
    private fun progress(uid: String) = cloud.collection(F.COLLECTION_PROGRESS).document(uid)
    private fun checkOwner(uid: String) = check(auth.currentUser?.uid == uid) { "Account changed" }

    private suspend fun all(query: Query): List<DocumentSnapshot> {
        val result = mutableListOf<DocumentSnapshot>()
        var next = query.orderBy(FieldPath.documentId()).limit(200)
        while (true) {
            val page = next.get(Source.SERVER).awaitResult().documents
            result += page
            if (page.size < 200) return result
            next = next.startAfter(page.last())
        }
    }

    /** Also used after recovery: a repaired quarantined record can correct an incomplete seed. */
    suspend fun ensureReady(uid: String) = bootstrapLock.withLock {
        checkOwner(uid)
        if (uid in readyAccounts) return@withLock
        // Persist the cutover barrier before scanning. Rules reject old session-only
        // writers after this point, so even a process death cannot reopen the race.
        checkOwner(uid)
        cloud.runTransaction { tx ->
            checkOwner(uid)
            val ref = progress(uid)
            val data = tx.get(ref).data.orEmpty()
            if (!data.containsKey(SessionProjectionPolicy.VERSION_KEY)) tx.set(ref, data + mapOf(
                SessionProjectionPolicy.VERSION_KEY to 1L, SessionProjectionPolicy.REVISION to 0L))
            Unit
        }.awaitResult()
        repeat(3) {
            checkOwner(uid)
            val before = progress(uid).get(Source.SERVER).awaitResult().data.orEmpty()
            if (before.long(SessionProjectionPolicy.VERSION_KEY) == SessionProjectionPolicy.VERSION &&
                before.long("projectionInvalidRecords") == 0L) {
                readyAccounts += uid
                return@withLock
            }
            val account = owner(uid).get(Source.SERVER).awaitResult().data.orEmpty()
            val sessions = all(owner(uid).collection("practiceRecords").whereEqualTo("type", "session"))
            val valid = sessions.filter { BackupRecordValidator.validate("practice", it.id, it.data.orEmpty()) == null }
            val entries = all(owner(uid).collection("japaEntries").whereEqualTo("source", "AUDIO_ACCEPTED"))
            val dates = entries.mapNotNull { doc ->
                val data = doc.data.orEmpty()
                if (BackupRecordValidator.validate("entry", doc.id, data) != null) null else {
                    val id = data["sessionStableId"] as? String ?: return@mapNotNull null
                    id to com.mantrasiddhi.domain.util.SandhyaDateKey.parse(data.text("dateKey")).toString()
                }
            }.toMap()
            val goalDocs = all(cloud.collection(F.COLLECTION_GOALS).whereEqualTo(F.FIELD_CHILD_UID, uid))
            // Fail visibly before attempting a transaction larger than Firestore's write limit.
            check(goalDocs.size <= 200) { "Assigned-goal repair requires a server migration for more than 200 goals" }
            val goals = goalDocs.associate { it.id to (it.data.orEmpty() + mapOf(
                F.FIELD_CURRENT_COUNT to 0L, F.FIELD_IS_COMPLETED to false)) }.toMutableMap()
            var summary = SessionProjectionPolicy.empty()
            for (doc in valid.sortedWith(compareBy<DocumentSnapshot> { it.data.orEmpty().number("createdAt") }.thenBy { it.id })) {
                val session = doc.data.orEmpty()
                summary = SessionProjectionPolicy.add(summary, session, SessionProjectionPolicy.date(session, dates[session.text("id")]))
                if (SessionProjectionPolicy.eligible(session, account[F.FIELD_ROLE] == "CHILD")) {
                    SessionProjectionPolicy.selectGoal(goals, session.number("createdAt"))?.let { id ->
                        goals[id] = SessionProjectionPolicy.credit(goals.getValue(id))
                    }
                }
            }
            val seed = summary + mapOf(SessionProjectionPolicy.REVISION to Math.addExact(before.long(SessionProjectionPolicy.REVISION), 1),
                "projectionInvalidRecords" to (sessions.size - valid.size).toLong(),
                "legacyDatePolicy" to "ledger-date-or-UTC", F.FIELD_UPDATED_AT to FieldValue.serverTimestamp())
            val committed = cloud.runTransaction { tx ->
                checkOwner(uid)
                val current = tx.get(progress(uid)).data.orEmpty()
                val currentAccount = tx.get(owner(uid)).data.orEmpty()
                val currentGoals = goalDocs.associate { it.id to tx.get(it.reference).data.orEmpty() }
                // New canonical session writes MUST change progress in the same transaction.
                // Retrying this outer scan includes sessions that raced with the seed query.
                if (current != before || currentAccount[F.FIELD_ROLE] != account[F.FIELD_ROLE] ||
                    goalDocs.any { currentGoals[it.id] != it.data.orEmpty() }) false else {
                    // Keep the pre-repair values recoverable when some historical source
                    // records exist only on an older/offline installation.
                    if (before.long(SessionProjectionPolicy.VERSION_KEY) < SessionProjectionPolicy.VERSION) {
                        fun archive(id: String, kind: String, value: Map<String, Any?>) {
                            tx.set(owner(uid).collection("projectionRepairs").document(id), mapOf(
                                "version" to 2L, "kind" to kind, "value" to value, "createdAt" to FieldValue.serverTimestamp()))
                        }
                        archive("v2_progress", "progress", before)
                        for (doc in goalDocs) archive("v2_goal_${doc.id}", "goal", doc.data.orEmpty())
                    }
                    tx.set(progress(uid), seed)
                    for (doc in goalDocs) {
                        val goal = goals.getValue(doc.id)
                        tx.update(doc.reference, mapOf(F.FIELD_CURRENT_COUNT to goal[F.FIELD_CURRENT_COUNT],
                            F.FIELD_IS_COMPLETED to goal[F.FIELD_IS_COMPLETED]))
                    }
                    true
                }
            }.awaitResult()
            if (committed) {
                if (valid.size == sessions.size) readyAccounts += uid
                return@withLock
            }
        }
        error("Session history changed during bootstrap; retry required")
    }

    suspend fun deliver(uid: String, source: Map<String, Any?>,
        event: com.mantrasiddhi.data.local.entity.CloudSyncEntity? = null) {
        val payload = SpeechRetention.forUpload(source)
        require(BackupRecordValidator.validate("practice", "session_${payload.text("id")}", payload) == null)
        ensureReady(uid)
        checkOwner(uid)
        val legacyChild = if (payload["childGoalEligible"] == null)
            owner(uid).get(Source.SERVER).awaitResult().getString(F.FIELD_ROLE) == "CHILD" else false
        val eligible = SessionProjectionPolicy.eligible(payload, legacyChild)
        val goals = if (eligible) all(cloud.collection(F.COLLECTION_GOALS)
            .whereEqualTo(F.FIELD_CHILD_UID, uid).whereEqualTo(F.FIELD_IS_COMPLETED, false)) else emptyList()
        val id = "session_${payload.text("id")}"
        val record = owner(uid).collection("practiceRecords").document(id)
        repeat(3) {
            var attemptedRevision = -1L
            try {
                cloud.runTransaction { tx ->
                    checkOwner(uid)
                    val old = tx.get(record)
                    val summary = tx.get(progress(uid)).data.orEmpty()
                    attemptedRevision = summary.long(SessionProjectionPolicy.REVISION)
                    val state = goals.associate { it.id to tx.get(it.reference).data.orEmpty() }
                    val legacyDate = if (payload["projectionDate"] == null && payload["entryId"] is String) {
                        val entry = tx.get(owner(uid).collection("japaEntries").document(payload.text("entryId"))).data.orEmpty()
                        if (BackupRecordValidator.validate("entry", payload.text("entryId"), entry) == null)
                            com.mantrasiddhi.domain.util.SandhyaDateKey.parse(entry.text("dateKey")).toString() else null
                    } else null
                    check(summary.long(SessionProjectionPolicy.VERSION_KEY) == SessionProjectionPolicy.VERSION)
                    if (!old.exists()) {
                        tx.set(record, payload)
                        tx.set(owner(uid).collection("backupChanges").document("practice_$id"), mapOf(
                            "type" to "practice", "id" to id, "value" to payload, "changedAt" to FieldValue.serverTimestamp()))
                        SessionProjectionPolicy.selectGoal(state.filterValues { it[F.FIELD_CHILD_UID] == uid }, payload.number("createdAt"))?.let { goalId ->
                            val updated = SessionProjectionPolicy.credit(state.getValue(goalId))
                            tx.update(goals.first { it.id == goalId }.reference, mapOf(
                                F.FIELD_CURRENT_COUNT to updated[F.FIELD_CURRENT_COUNT], F.FIELD_IS_COMPLETED to updated[F.FIELD_IS_COMPLETED]))
                        }
                    }
                    @Suppress("UNCHECKED_CAST")
                    val checkpoints = (summary[CloudSyncPolicy.CHECKPOINTS] as? Map<String, Any?>).orEmpty()
                    if (!old.exists() || (event != null && !CloudSyncPolicy.acknowledged(checkpoints, event))) {
                        val updated = if (old.exists()) summary else SessionProjectionPolicy.add(summary, payload,
                            SessionProjectionPolicy.date(payload, legacyDate))
                        tx.set(progress(uid), updated + mapOf(F.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                            CloudSyncPolicy.CHECKPOINTS to if (event == null) checkpoints else checkpoints + (event.streamId to event.id)))
                    }
                    Unit
                }.awaitResult()
                return
            } catch (failure: FirebaseFirestoreException) {
                checkOwner(uid)
                // On Android's optimistic transactions, rules may reject a stale count
                // before Firestore reports the read precondition conflict. Retry only
                // when another committed projection demonstrably advanced the revision.
                if (failure.code != FirebaseFirestoreException.Code.PERMISSION_DENIED || attemptedRevision < 0 ||
                    progress(uid).get(Source.SERVER).awaitResult().data.orEmpty().long(SessionProjectionPolicy.REVISION) == attemptedRevision) throw failure
            }
        }
        error("Concurrent session projections require a later retry")
    }
}
