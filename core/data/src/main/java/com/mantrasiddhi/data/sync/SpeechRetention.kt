package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.entity.RecitationSessionEntity

/** Cloud speech details expire; scores, occurrence time and session identity remain immutable. */
internal object SpeechRetention {
    const val PERIOD_MS = 365L * 24 * 60 * 60 * 1000

    fun forUpload(value: Map<String, Any?>, now: Long = System.currentTimeMillis()): Map<String, Any?> =
        if (value["type"] == "session" && value.number("createdAt") <= now - PERIOD_MS)
            value + mapOf("transcription" to "", "wordScoresJson" to "") else value

    fun compatible(old: RecitationSessionEntity, incoming: RecitationSessionEntity,
        now: Long = System.currentTimeMillis()): Boolean {
        val normalized = incoming.copy(acceptedEntryId = old.acceptedEntryId)
        return old == normalized || (incoming.createdAt <= now - PERIOD_MS &&
            incoming.transcription.isEmpty() && incoming.wordScoresJson.isEmpty() &&
            old.copy(transcription = "", wordScoresJson = "") == normalized)
    }
}
