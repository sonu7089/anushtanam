package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpeechRetentionTest {
    private val now = 2 * SpeechRetention.PERIOD_MS
    private fun session(createdAt: Long = now - SpeechRetention.PERIOD_MS) = RecitationSessionEntity(
        id = 1, userId = 7, mantraId = "gayatri", mode = "NORMAL", overallScore = .9,
        wordScoresJson = "om:0.9", transcription = "om", isCorrect = true,
        durationMs = 1000, createdAt = createdAt, stableId = "session-one")

    @Test fun `old offline uploads retain full speech at and beyond the former expiry boundary`() {
        val value = PracticeRecordCodec.session(session(), null)
        assertEquals(value, SpeechRetention.forUpload(value, now))
        assertEquals(value, SpeechRetention.forUpload(value, now + SpeechRetention.PERIOD_MS))
        assertEquals(value, SpeechRetention.forUpload(value, now - 1))
        assertEquals("om", value["transcription"])
    }

    @Test fun `restore accepts only expired speech redaction and preserves assessment integrity`() {
        val old = session()
        val redacted = old.copy(transcription = "", wordScoresJson = "")
        assertTrue(SpeechRetention.compatible(old, redacted, now))
        assertFalse(SpeechRetention.compatible(old, redacted, now - 1))
        assertFalse(SpeechRetention.compatible(old, redacted.copy(overallScore = .5), now))
        assertFalse(SpeechRetention.compatible(old, redacted.copy(createdAt = 0), now))
        assertFalse(SpeechRetention.compatible(old, old.copy(transcription = "changed"), now))
        assertFalse(SpeechRetention.compatible(redacted, old, now))
    }
}
