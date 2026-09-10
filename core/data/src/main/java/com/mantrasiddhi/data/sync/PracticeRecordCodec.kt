package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.entity.*

/** Explicit fields keep local IDs and account credentials out of the wire format. */
internal object PracticeRecordCodec {
    fun session(row: RecitationSessionEntity, entryId: String?) = mapOf(
        "type" to "session", "id" to row.stableId, "entryId" to entryId,
        "mantraId" to row.mantraId, "mode" to row.mode, "overallScore" to row.overallScore,
        "wordScoresJson" to row.wordScoresJson, "transcription" to row.transcription,
        "isCorrect" to row.isCorrect, "durationMs" to row.durationMs, "createdAt" to row.createdAt,
        "correctness" to row.correctness, "completeness" to row.completeness, "pronunciation" to row.pronunciation
    )

    fun session(value: Map<String, Any?>, userId: Long, localId: Long) = RecitationSessionEntity(
        id = localId, userId = userId, stableId = value.text("id"), mantraId = value.text("mantraId"),
        mode = value.text("mode"), overallScore = value.score("overallScore"),
        wordScoresJson = value.text("wordScoresJson"), transcription = value.text("transcription"),
        isCorrect = value["isCorrect"] == true, durationMs = value.number("durationMs"),
        createdAt = value.number("createdAt"), correctness = value.score("correctness"),
        completeness = value.score("completeness"), pronunciation = value.score("pronunciation")
    )

    fun log(row: SandhyaLogEntity) = mapOf(
        "type" to "sandhya", "id" to "${row.dateKey}_${row.period}", "dateKey" to row.dateKey,
        "period" to row.period, "scheduledEpochMillis" to row.scheduledEpochMillis,
        "status" to row.status, "observedEpochMillis" to row.observedEpochMillis
    )

    fun log(value: Map<String, Any?>, userId: Long, localId: Long) = SandhyaLogEntity(
        id = localId, userId = userId, dateKey = value.text("dateKey"), period = value.text("period"),
        scheduledEpochMillis = value.number("scheduledEpochMillis"), status = value.text("status"),
        observedEpochMillis = value.numberOrNull("observedEpochMillis")
    )

    // An observed practice must survive a missed reminder on another phone.
    fun mergeLog(first: Map<String, Any?>, second: Map<String, Any?>): Map<String, Any?> {
        val rank = mapOf("NOTIFIED" to 0, "MISSED" to 1, "OBSERVED" to 2)
        return listOf(first, second).maxWith(compareBy<Map<String, Any?>> { rank[it.text("status")] ?: -1 }
            .thenBy { it.number("observedEpochMillis") }.thenBy { it.number("scheduledEpochMillis") })
    }

    fun change(row: SandhyaCountChangeEntity) = mapOf(
        "type" to "change", "id" to row.id, "dateKey" to row.dateKey, "period" to row.period,
        "zoneId" to row.zoneId, "mode" to row.mode, "previousQuantity" to row.previousQuantity,
        "quantity" to row.quantity, "revision" to row.revision, "createdAtMillis" to row.createdAtMillis,
        "targetCompleted" to row.targetCompleted, "vowCompleted" to row.vowCompleted,
        "completionReversed" to row.completionReversed, "milestonesCrossed" to row.milestonesCrossed
    )

    fun change(value: Map<String, Any?>, userId: Long) = SandhyaCountChangeEntity(
        value.text("id"), userId, value.text("dateKey"), value.text("period"), value.text("zoneId"),
        value.text("mode"), value.numberOrNull("previousQuantity"), value.number("quantity"),
        value.number("revision"), value.number("createdAtMillis"), value["targetCompleted"] == true,
        value["vowCompleted"] == true, value["completionReversed"] == true, value.number("milestonesCrossed").toInt()
    )

    private fun Map<String, Any?>.score(key: String) = (get(key) as? Number)?.toDouble() ?: 0.0
}
