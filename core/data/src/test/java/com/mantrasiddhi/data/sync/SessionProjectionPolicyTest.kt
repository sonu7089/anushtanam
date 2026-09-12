package com.mantrasiddhi.data.sync

import com.google.firebase.Timestamp
import com.mantrasiddhi.data.remote.FirebaseContract as F
import com.mantrasiddhi.data.sync.CloudSyncPolicy.long
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date

class SessionProjectionPolicyTest {
    private fun session(score: Double, correct: Boolean, mode: String = "NORMAL", eligible: Boolean = correct) = mapOf(
        "id" to "session", "createdAt" to Instant.parse("2026-09-11T20:00:00Z").toEpochMilli(),
        "overallScore" to score, "isCorrect" to correct, "mode" to mode, "childGoalEligible" to eligible,
        "projectionDate" to "2026-09-12", "projectionZoneId" to "Asia/Kolkata")

    @Test fun `daily seed includes failed attempts and actual score sums on each original day`() {
        var summary = SessionProjectionPolicy.empty()
        summary = SessionProjectionPolicy.add(summary, session(.9, true))
        summary = SessionProjectionPolicy.add(summary, session(.2, false))
        summary = SessionProjectionPolicy.add(summary, session(.6, true, "PRACTICE", false), "2026-09-11")
        assertEquals(3L, summary[F.FIELD_TOTAL_SESSIONS])
        assertEquals(2L, summary[F.FIELD_CORRECT_SESSIONS])
        assertEquals(1.7, (summary[F.FIELD_SCORE_SUM] as Double), .000001)
        val days = summary[F.FIELD_DAILY] as Map<*, *>
        assertEquals(mapOf("n" to 2L, "c" to 1L, "s" to 1.1), days["2026-09-12"])
        assertEquals(mapOf("n" to 1L, "c" to 1L, "s" to .6), days["2026-09-11"])
    }

    @Test fun `only accepted normal sessions with captured child eligibility earn rewards`() {
        assertTrue(SessionProjectionPolicy.eligible(session(.9, true), false))
        assertFalse(SessionProjectionPolicy.eligible(session(.9, true, eligible = false), true))
        assertFalse(SessionProjectionPolicy.eligible(session(.9, true, "PRACTICE"), true))
        assertFalse(SessionProjectionPolicy.eligible(session(.2, false), true))
        assertFalse(SessionProjectionPolicy.eligible(mapOf("source" to "SANDHYA", "quantity" to 108L), true))
        assertTrue(SessionProjectionPolicy.eligible(session(.9, true) - "childGoalEligible", true))
        assertFalse(SessionProjectionPolicy.eligible(session(.9, true) - "childGoalEligible", false))
    }

    @Test fun `legacy date fallback is deterministic and prefers original ledger date`() {
        val legacy = session(.9, true) - "projectionDate" - "projectionZoneId"
        assertEquals("2026-09-11", SessionProjectionPolicy.date(legacy))
        assertEquals("2026-09-12", SessionProjectionPolicy.date(legacy, "2026-09-12"))
        assertEquals("2026-09-12", SessionProjectionPolicy.date(session(.9, true), "2000-01-01"))
    }

    @Test fun `goal eligibility uses occurrence time stable ties and exact caps`() {
        fun goal(time: Long, count: Long = 0) = mapOf(F.FIELD_CREATED_AT to Timestamp(Date(time)),
            F.FIELD_CURRENT_COUNT to count, F.FIELD_TARGET_COUNT to 1L)
        val goals = linkedMapOf<String, Map<String, Any?>>("b" to goal(1), "future" to goal(100), "a" to goal(1))
        assertNull(SessionProjectionPolicy.selectGoal(goals, 0))
        assertEquals("a", SessionProjectionPolicy.selectGoal(goals, 10))
        goals["a"] = SessionProjectionPolicy.credit(goals.getValue("a"))
        assertEquals(true, goals.getValue("a")[F.FIELD_IS_COMPLETED])
        assertEquals("b", SessionProjectionPolicy.selectGoal(goals, 10))
        goals["b"] = SessionProjectionPolicy.credit(goals.getValue("b"))
        assertNull(SessionProjectionPolicy.selectGoal(goals, 10))
        assertEquals(1L, SessionProjectionPolicy.credit(goals.getValue("b")).long(F.FIELD_CURRENT_COUNT))
    }

    @Test fun `daily retention does not truncate lifetime totals or resurrect an old day`() {
        var summary = SessionProjectionPolicy.empty()
        repeat(120) { summary = SessionProjectionPolicy.add(summary, session(.5, false),
            java.time.LocalDate.of(2026, 1, 1).plusDays(it.toLong()).toString()) }
        summary = SessionProjectionPolicy.add(summary, session(.5, false), "2000-01-01")
        assertEquals(121L, summary[F.FIELD_TOTAL_SESSIONS])
        val daily = summary[F.FIELD_DAILY] as Map<*, *>
        assertEquals(com.mantrasiddhi.domain.model.ProgressSummary.DAILY_KEY_CAP, daily.size)
        assertFalse(daily.containsKey("2000-01-01"))
    }
}
