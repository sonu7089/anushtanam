package com.mantrasiddhi.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotificationNudgeTest {
    private val now = 1800000000000L
    private val note = NotificationNudge("festival-1", "A little something for the festival",
        "Your festival offer is ready. Tap to take a look.", now + 60_000)

    @Test fun `standalone notification needs no content ID and survives persistence`() {
        assertFalse(note.toPayload().containsKey("contentId"))
        assertEquals(note, NotificationNudge.fromPayload(note.toPayload(), now))
    }

    @Test fun `invalid unsupported and expired payloads are ignored`() {
        listOf(mapOf("kind" to "teaching"), mapOf("schemaVersion" to "2"),
            mapOf("destination" to "https://example.com"), mapOf("destination" to "offers"),
            mapOf("campaignId" to "../private"), mapOf("title" to " "), mapOf("body" to ""),
            mapOf("title" to "a".repeat(101)), mapOf("body" to "a".repeat(281)),
            mapOf("expiresAt" to now.toString()), mapOf("expiresAt" to "oops"),
            mapOf("expiresAt" to Long.MAX_VALUE.toString())).forEach {
            assertNull(NotificationNudge.fromPayload(note.toPayload() + it, now), it.toString())
        }
        note.toPayload().keys.forEach { assertNull(NotificationNudge.fromPayload(note.toPayload() - it, now)) }
    }

    @Test fun `japa preference and expiry still control standalone delivery`() {
        assertFalse(TeachingDeliveryPolicy.canDisplay(true, true, note.expiresAt, now))
        assertFalse(TeachingDeliveryPolicy.canDisplay(false, false, note.expiresAt, now))
        assertFalse(TeachingDeliveryPolicy.canDisplay(true, false, now, now))
        assertTrue(TeachingDeliveryPolicy.canDisplay(true, false, note.expiresAt, now))
    }
}
