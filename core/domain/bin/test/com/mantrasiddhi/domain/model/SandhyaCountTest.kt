package com.mantrasiddhi.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SandhyaCountTest {
    @Test fun addsAndAbsoluteCorrectionsHaveDifferentMeaning() {
        val evening = SandhyaCountMath.total(64, 536, SandhyaCountMode.ADD)
        assertEquals(600L, evening)
        assertEquals(740L, 108 + 32 + evening)
        assertEquals(500L, SandhyaCountMath.total(evening, 500, SandhyaCountMode.EDIT))
        assertEquals(600L, SandhyaCountMath.total(100, 600, SandhyaCountMode.EDIT))
        assertEquals(40L, SandhyaCountMath.total(null, 40, SandhyaCountMode.RECORD))
        assertEquals(0L, SandhyaCountMath.total(600, 0, SandhyaCountMode.EDIT))
    }

    @Test fun rejectsInvalidCountsAndOverflow() {
        listOf("", "-1", "1.5", "1e3", "abc", " 1", "9223372036854775808").forEach {
            assertNull(SandhyaCountMath.parse(it), it)
        }
        assertEquals(Long.MAX_VALUE, SandhyaCountMath.parse(Long.MAX_VALUE.toString()))
        assertThrows(ArithmeticException::class.java) {
            SandhyaCountMath.total(Long.MAX_VALUE, 1, SandhyaCountMode.ADD)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SandhyaCountMath.total(null, -1, SandhyaCountMode.RECORD)
        }
    }

    @Test fun rejectsFutureAndInvalidDates() {
        val today = LocalDate.of(2026, 9, 8)
        SandhyaCountMath.validateDate("20260908", "Asia/Kolkata", today)
        assertThrows(IllegalArgumentException::class.java) {
            SandhyaCountMath.validateDate("20260909", "Asia/Kolkata", today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SandhyaCountMath.validateDate("20260230", "Asia/Kolkata", today)
        }
    }
}
