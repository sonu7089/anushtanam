package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.entity.*
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PracticeRecordCodecTest {
    @Test fun `all performance fields survive serialization and a new local profile`() {
        val original = RecitationSessionEntity(42, 7, "gayatri", "PRACTICE", .81,
            "om:0.91,tat:0.72", "om tat", false, 23456, 1789000000000, .82, .83, .79, "stable-session")
        val wire = CloudSyncPolicy.decode(JSONObject(PracticeRecordCodec.session(original, null)).toString())
        assertEquals(original.copy(id = 9, userId = 17), PracticeRecordCodec.session(wire, 17, 9))
        assertFalse(wire.containsKey("userId"))
    }

    @Test fun `corrections preserve large integer counts and milestone results`() {
        val original = SandhyaCountChangeEntity("change", 7, "20260910", "SAYAM", "Asia/Kolkata",
            "EDIT", Long.MAX_VALUE, Long.MAX_VALUE - 1, 5, 123, true, false, true, 2)
        val wire = CloudSyncPolicy.decode(JSONObject(PracticeRecordCodec.change(original)).toString())
        assertEquals(original.copy(userId = 17), PracticeRecordCodec.change(wire, 17))
    }

    @Test fun `observed wins in either delivery order without confusing an unknown count with zero`() {
        val missed = PracticeRecordCodec.log(SandhyaLogEntity(1, 7, "SAYAM", 100, "MISSED", null, "20260910"))
        val observed = missed + mapOf("status" to "OBSERVED", "observedEpochMillis" to 120L)
        assertEquals(observed, PracticeRecordCodec.mergeLog(missed, observed))
        assertEquals(observed, PracticeRecordCodec.mergeLog(observed, missed))
        assertFalse(observed.containsKey("quantity"))
    }
}
