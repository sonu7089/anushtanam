package com.mantrasiddhi.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JapaAccountingTest {
    @Test fun `overshoot survives a correction until the eligible total falls below the goal`() {
        assertEquals(500, JapaAccounting.credited(0, listOf(108, 32, 600), 500))
        assertEquals(500, JapaAccounting.credited(0, listOf(108, 32, 500), 500))
        assertEquals(440, JapaAccounting.credited(0, listOf(108, 32, 300), 500))
    }

    @Test fun `lifetime sized quantities cannot overflow capped goal arithmetic`() {
        assertEquals(2_400_000, JapaAccounting.credited(108, listOf(Long.MAX_VALUE, Long.MAX_VALUE), 2_400_000))
        assertThrows(IllegalArgumentException::class.java) { JapaAccounting.credited(0, listOf(-1), 108) }
    }

    @Test fun `bulk additions detect every crossed milestone without requiring equality`() {
        assertEquals(listOf(PurashcharanaStage.ARAMBHA, PurashcharanaStage.DASHA_SAHASRA),
            JapaAccounting.crossed(900, 10_600, 24))
        assertTrue(JapaAccounting.crossed(10_600, 10_600, 24).isEmpty())
        assertTrue(JapaAccounting.crossed(10_600, 900, 24).isEmpty())
    }
}
