package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BackupRecordValidatorTest {
    private fun entry() = JapaEntryEntity("e", 7, "gayatri", "20260910", "Asia/Kolkata", "SAYAM",
        108, 1, 10, null, null, "SANDHYA").payload()

    @Test fun `shipped unversioned records and explicit v1 validate without coercion`() {
        assertNull(BackupRecordValidator.validate("entry", "e", entry()))
        assertNull(BackupRecordValidator.validate("entry", "e", entry() + ("schemaVersion" to 1L)))
        assertNull(BackupRecordValidator.validate("entry", "e", entry() + ("quantity" to Long.MAX_VALUE)))
        assertNull(BackupRecordValidator.validate("entry", "e", entry() + mapOf("period" to null, "source" to "AUDIO_ACCEPTED", "quantity" to 1L, "sessionStableId" to "s")))
    }

    @Test fun `every required entry field is required and extra fields or future schemas are quarantinable`() {
        for (key in entry().keys) assertNotNull(BackupRecordValidator.validate("entry", "e", entry() - key), key)
        assertNotNull(BackupRecordValidator.validate("entry", "wrong", entry()))
        assertNotNull(BackupRecordValidator.validate("entry", "e", entry() + ("extra" to true)))
        assertNotNull(BackupRecordValidator.validate("entry", "e", entry() + ("schemaVersion" to 2)))
        assertNotNull(BackupRecordValidator.validate("future", "e", entry()))
    }

    @Test fun `full calendar timezone numeric and source checks reject malformed records`() {
        for ((key, value) in listOf("dateKey" to "20260230", "dateKey" to "20260931", "dateKey" to "00000101",
            "zoneId" to "not/a/zone", "quantity" to -1L, "quantity" to 1.5, "quantity" to "108",
            "revision" to 0, "revision" to Long.MAX_VALUE, "period" to null, "source" to "unknown",
            "createdAtMillis" to -1, "updatedAtMillis" to 0, "targetId" to 9, "sessionStableId" to "s")) {
            assertNotNull(BackupRecordValidator.validate("entry", "e", entry() + (key to value)), "$key=$value")
        }
        assertNull(BackupRecordValidator.validate("entry", "e", entry() + ("dateKey" to "20240229")))
    }

    @Test fun `goal target and akshara narrowing is checked`() {
        val goal = mapOf("id" to "g", "kind" to "target", "mantraId" to "gayatri", "startedAtMillis" to 1L,
            "endedAtMillis" to null, "baselineCount" to 0L, "targetCount" to Int.MAX_VALUE, "rewardType" to "OTHER", "rewardDescription" to "")
        assertNull(BackupRecordValidator.validate("goal", "g", goal))
        assertNotNull(BackupRecordValidator.validate("goal", "g", goal + ("targetCount" to Int.MAX_VALUE.toLong() + 1)))
        val vow = goal - "rewardType" - "rewardDescription" + mapOf("kind" to "vow", "aksharas" to 24, "completedAtMillis" to null)
        assertNull(BackupRecordValidator.validate("goal", "g", vow))
        assertNotNull(BackupRecordValidator.validate("goal", "g", vow + ("aksharas" to 2147483648L)))
        for (key in goal.keys) assertNotNull(BackupRecordValidator.validate("goal", "g", goal - key), key)
    }

    @Test fun `all performance variants validate emitted payloads and reject bad scores and identities`() {
        val session = PracticeRecordCodec.session(RecitationSessionEntity(userId = 7, mantraId = "gayatri", mode = "NORMAL",
            overallScore = .9, wordScoresJson = "om:0.9", transcription = "om", isCorrect = true, durationMs = 200, createdAt = 10, stableId = "s"), "e")
        assertNull(BackupRecordValidator.validate("practice", "session_s", session))
        for (score in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1))
            assertNotNull(BackupRecordValidator.validate("practice", "session_s", session + ("overallScore" to score)))
        for (words in listOf("om:NaN", "om:Infinity", "om:1.5", "om", ":0.5"))
            assertNotNull(BackupRecordValidator.validate("practice", "session_s", session + ("wordScoresJson" to words)))
        val log = PracticeRecordCodec.log(SandhyaLogEntity(userId = 7, period = "SAYAM", scheduledEpochMillis = 1,
            status = "OBSERVED", observedEpochMillis = 2, dateKey = "20260910"))
        assertNull(BackupRecordValidator.validate("practice", "sandhya_20260910_SAYAM", log))
        assertNotNull(BackupRecordValidator.validate("practice", "sandhya_wrong", log + ("id" to "wrong")))
        val change = PracticeRecordCodec.change(SandhyaCountChangeEntity("c", 7, "20260910", "SAYAM", "UTC", "EDIT", 1, 2, 1, 10))
        assertNull(BackupRecordValidator.validate("practice", "change_c", change))
        assertNotNull(BackupRecordValidator.validate("practice", "change_c", change + ("previousQuantity" to -1)))
        assertNotNull(BackupRecordValidator.validate("practice", "change_c", change + ("milestonesCrossed" to 2147483648L)))
    }

    @Test fun `both conflict revisions must be complete validated records for the same entry`() {
        val conflict = mapOf("id" to "conflict", "entryId" to "e", "first" to entry(), "second" to entry(), "resolved" to false)
        assertNull(BackupRecordValidator.validate("conflict", "conflict", conflict))
        assertNotNull(BackupRecordValidator.validate("conflict", "conflict", conflict + ("second" to (entry() + ("id" to "other")))))
        assertNotNull(BackupRecordValidator.validate("conflict", "conflict", conflict + ("first" to (entry() - "zoneId"))))
    }
}
