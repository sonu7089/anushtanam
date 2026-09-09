package com.mantrasiddhi.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SandhyaReminderPolicyTest {
    private val event = 2_000_000L
    @Test fun `preparation is fifteen minutes before and event has its own time`() {
        assertEquals(1_100_000L, SandhyaReminderPolicy.trigger(event, SandhyaReminderStage.PREPARATION, 0))
        assertEquals(event, SandhyaReminderPolicy.trigger(event, SandhyaReminderStage.EVENT, 0))
    }
    @Test fun `late enablement keeps event and offers one preparation cue`() {
        assertEquals(1_401_000L, SandhyaReminderPolicy.trigger(event, SandhyaReminderStage.PREPARATION, 1_400_000L))
        assertEquals(event, SandhyaReminderPolicy.trigger(event, SandhyaReminderStage.EVENT, 1_400_000L))
    }
    @Test fun `no back to back cue within two minutes`() {
        assertNull(SandhyaReminderPolicy.trigger(event, SandhyaReminderStage.PREPARATION, event - 120_000))
        assertEquals(event, SandhyaReminderPolicy.trigger(event, SandhyaReminderStage.EVENT, event - 120_000))
    }
    @Test fun `past events are never scheduled`() {
        SandhyaReminderStage.entries.forEach { assertNull(SandhyaReminderPolicy.trigger(event, it, event)) }
    }
    @Test fun `delayed preparation coalesces to event only when event is enabled`() {
        assertEquals(SandhyaReminderStage.EVENT, SandhyaReminderPolicy.delivery(SandhyaReminderStage.PREPARATION, event, event, true))
        assertNull(SandhyaReminderPolicy.delivery(SandhyaReminderStage.PREPARATION, event, event, false))
    }
    @Test fun `stale delivery is suppressed`() {
        assertNull(SandhyaReminderPolicy.delivery(SandhyaReminderStage.EVENT, event, event + 3_600_001, true))
    }
}
