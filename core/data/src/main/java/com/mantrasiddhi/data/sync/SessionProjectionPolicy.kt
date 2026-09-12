package com.mantrasiddhi.data.sync

import com.google.firebase.Timestamp
import com.mantrasiddhi.data.remote.FirebaseContract as F
import com.mantrasiddhi.data.sync.CloudSyncPolicy.long
import com.mantrasiddhi.data.sync.CloudSyncPolicy.double
import java.time.Instant
import java.time.ZoneOffset

/** The immutable canonical session is the receipt, shared by every device and replay. */
internal object SessionProjectionPolicy {
    const val VERSION = 2L
    const val VERSION_KEY = "sessionProjectionVersion"
    const val REVISION = "sessionProjectionRevision"

    fun empty(): Map<String, Any?> = mapOf(F.FIELD_TOTAL_SESSIONS to 0L,
        F.FIELD_CORRECT_SESSIONS to 0L, F.FIELD_SCORE_SUM to 0.0,
        F.FIELD_BEST_SCORE to 0.0, F.FIELD_DAILY to emptyMap<String, Any?>(),
        VERSION_KEY to VERSION, REVISION to 0L)

    // Pre-v16 failed/practice sessions have no original zone. UTC is a stable fallback;
    // accepted legacy audio can use the original date on its canonical ledger entry.
    fun date(session: Map<String, Any?>, legacyEntryDate: String? = null): String =
        session["projectionDate"] as? String ?: legacyEntryDate ?:
        Instant.ofEpochMilli(session.number("createdAt")).atZone(ZoneOffset.UTC).toLocalDate().toString()

    fun add(summary: Map<String, Any?>, session: Map<String, Any?>, date: String = date(session)): Map<String, Any?> =
        CloudSyncPolicy.progress(summary, mapOf("score" to session.double("overallScore"),
            "correct" to session["isCorrect"], "date" to date)) +
            (REVISION to Math.addExact(summary.long(REVISION), 1))

    fun eligible(session: Map<String, Any?>, legacyChild: Boolean): Boolean =
        session["mode"] == "NORMAL" && session["isCorrect"] == true &&
            (session["childGoalEligible"] as? Boolean ?: legacyChild)

    fun selectGoal(goals: Map<String, Map<String, Any?>>, occurredAt: Long): String? = goals.entries
        .filter { (_, goal) -> goal.long(F.FIELD_CURRENT_COUNT) < goal.long(F.FIELD_TARGET_COUNT) &&
            (goal[F.FIELD_CREATED_AT] as? Timestamp)?.toDate()?.time?.let { it <= occurredAt } == true }
        .minWithOrNull(compareBy<Map.Entry<String, Map<String, Any?>>> {
            (it.value[F.FIELD_CREATED_AT] as Timestamp).toDate().time
        }.thenBy { it.key })?.key

    fun credit(goal: Map<String, Any?>): Map<String, Any?> {
        val count = minOf(Math.addExact(goal.long(F.FIELD_CURRENT_COUNT), 1), goal.long(F.FIELD_TARGET_COUNT))
        return goal + mapOf(F.FIELD_CURRENT_COUNT to count, F.FIELD_IS_COMPLETED to (count == goal.long(F.FIELD_TARGET_COUNT)))
    }
}
