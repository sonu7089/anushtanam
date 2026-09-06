package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import com.mantrasiddhi.data.remote.FirebaseContract as F
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CloudSyncPolicyTest {
    private val seed = mapOf<String, Any?>(F.FIELD_TOTAL_SESSIONS to 10L, F.FIELD_CORRECT_SESSIONS to 8L,
        F.FIELD_SCORE_SUM to 8.0, F.FIELD_BEST_SCORE to 0.95, F.FIELD_DAILY to emptyMap<String, Any>())
    private val payload = mapOf("score" to 0.9, "correct" to true, "date" to "2026-09-06", "seed" to seed)

    @Test fun `missing remote uses the captured seed without counting the triggering attempt twice`() {
        assertEquals(seed, CloudSyncPolicy.progress(emptyMap(), payload))
        val next = CloudSyncPolicy.progress(seed, payload)
        assertEquals(11L, next[F.FIELD_TOTAL_SESSIONS])
        assertEquals(9L, next[F.FIELD_CORRECT_SESSIONS])
        assertEquals(8.9, next[F.FIELD_SCORE_SUM])
    }

    @Test fun `delayed updates retain the recitation date across midnight`() {
        val updated = CloudSyncPolicy.progress(seed, payload + ("date" to "2026-09-05"))
        assertTrue((updated[F.FIELD_DAILY] as Map<*, *>).containsKey("2026-09-05"))
        assertEquals(0.95, updated[F.FIELD_BEST_SCORE])
    }

    @Test fun `checkpoint skips acknowledged replay but isolates installations`() {
        val event = CloudSyncEntity(7, "owner", "first-device", SyncKind.PROGRESS, "{}", 0)
        assertTrue(CloudSyncPolicy.acknowledged(mapOf("first-device" to 7L), event))
        assertTrue(CloudSyncPolicy.acknowledged(mapOf("first-device" to 8L), event))
        assertFalse(CloudSyncPolicy.acknowledged(mapOf("first-device" to 6L), event))
        assertFalse(CloudSyncPolicy.acknowledged(mapOf("another-device" to 8L), event))
    }

    @Test fun `purashcharana never lowers cloud count and preserves pending withdrawal`() {
        val remote = mapOf<String, Any?>(F.FIELD_MANTRA_ID to "gayatri", F.FIELD_AKSHARA_COUNT to 24,
            F.FIELD_TARGET_JAPA_COUNT to 2400000L, F.FIELD_CURRENT_JAPA_COUNT to 1000L,
            F.FIELD_STARTED_AT to 100L, F.FIELD_IS_ACTIVE to true)
        val local = remote + mapOf(F.FIELD_CURRENT_JAPA_COUNT to 900L, F.FIELD_STARTED_AT to 200L, F.FIELD_IS_ACTIVE to false)
        val merged = CloudSyncPolicy.purashcharana(remote, local)
        assertEquals(1000L, merged[F.FIELD_CURRENT_JAPA_COUNT])
        assertEquals(100L, merged[F.FIELD_STARTED_AT])
        assertEquals(false, merged[F.FIELD_IS_ACTIVE])
    }

    @Test fun `JSON queue payload preserves nested numeric and null values`() {
        val decoded = CloudSyncPolicy.decode("""{"seed":{"totalSessions":5,"scoreSum":4.5},"completedAt":null,"correct":true}""")
        assertEquals(5, (decoded["seed"] as Map<*, *>)["totalSessions"])
        assertNull(decoded["completedAt"])
        assertEquals(true, decoded["correct"])
    }
}
