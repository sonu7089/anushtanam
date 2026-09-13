package com.mantrasiddhi.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TeachingDeliveryPolicyTest {
    @Test fun `permission and practice are checked again before display`() {
        assertFalse(TeachingDeliveryPolicy.canDisplay(false, false, 200, 100))
        assertFalse(TeachingDeliveryPolicy.canDisplay(true, true, 200, 100))
        assertTrue(TeachingDeliveryPolicy.canDisplay(true, false, 200, 100))
    }
    @Test fun `ending practice never releases an expired message`() {
        assertFalse(TeachingDeliveryPolicy.canDisplay(true, false, 100, 100))
        assertFalse(TeachingDeliveryPolicy.canDisplay(true, false, 99, 100))
    }
    @Test fun `messages respect both ends of the publication window`() {
        val message = TeachingMessage("note", 1, "Title", "Summary", "Body", "", 100, 200)
        assertFalse(message.isVisible(99))
        assertTrue(message.isVisible(100))
        assertFalse(message.isVisible(200))
    }
    @Test fun `routes reject paths and arbitrary urls`() {
        assertTrue(TeachingDeliveryPolicy.validId("morning_note-1"))
        listOf("", "../draft", "https://example.com", "hello?screen=other").forEach {
            assertFalse(TeachingDeliveryPolicy.validId(it))
        }
    }
}
