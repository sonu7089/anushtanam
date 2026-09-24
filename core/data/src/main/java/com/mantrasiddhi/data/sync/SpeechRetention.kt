package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.entity.RecitationSessionEntity

/** Full history is retained. Compatibility accepts redactions made by older server releases. */
internal object SpeechRetention {
    const val PERIOD_MS = 365L * 24 * 60 * 60 * 1000

    @Suppress("UNUSED_PARAMETER")
    fun forUpload(value: Map<String, Any?>, now: Long = System.currentTimeMillis()): Map<String, Any?> = value

    fun compatible(old: RecitationSessionEntity, incoming: RecitationSessionEntity,
        now: Long = System.currentTimeMillis()): Boolean {
        val normalized = incoming.copy(acceptedEntryId = old.acceptedEntryId)
        return old == normalized || (incoming.createdAt <= now - PERIOD_MS &&
            incoming.transcription.isEmpty() && incoming.wordScoresJson.isEmpty() &&
            old.copy(transcription = "", wordScoresJson = "") == normalized)
    }
}
