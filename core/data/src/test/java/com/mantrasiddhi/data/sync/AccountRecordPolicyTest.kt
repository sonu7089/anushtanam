package com.mantrasiddhi.data.sync

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AccountRecordPolicyTest {
    private fun record(id: String = "preference_minimum_PRATAH", time: Long = 100,
        operation: String = "a", data: Map<String, Any?> = mapOf("value" to Long.MAX_VALUE), deleted: Boolean = false) = mapOf(
        "id" to id, "updatedAtMillis" to time, "operationId" to operation, "deleted" to deleted,
        "dataJson" to BackupFingerprint.encode(data))

    @Test fun `large minimums survive wire roundtrip without precision loss`() {
        val row = CloudSyncPolicy.decode(BackupFingerprint.encode(record()))
        assertNull(AccountRecordPolicy.validate(row.text("id"), row))
        assertEquals(Long.MAX_VALUE, AccountRecordStore.data(row)["value"])
    }

    @Test fun `offline writes have deterministic ordering and bootstrap cannot replace a remote edit`() {
        val a = record(operation = "a")
        val b = record(operation = "b")
        assertTrue(AccountRecordPolicy.newer(b, a))
        assertFalse(AccountRecordPolicy.newer(a, b))
        assertFalse(AccountRecordPolicy.newer(a, a))
        assertFalse(AccountRecordPolicy.newer(record(time = 0, operation = "z"), a))
        assertTrue(AccountRecordPolicy.newer(record(time = 0), null))
    }

    @Test fun `saved or discarded drafts cannot be resurrected by a later editor clock`() {
        val edit = record(id = "draft_one", time = 500)
        val terminal = record(id = "draft_one", time = 100, deleted = true, data = mapOf("deleted" to true))
        assertNull(AccountRecordPolicy.validate("draft_one", terminal))
        assertTrue(AccountRecordPolicy.newer(terminal, edit))
        assertFalse(AccountRecordPolicy.newer(edit, terminal))
    }

    @Test fun `malformed preferences and unsupported fields are rejected before restore`() {
        for (bad in listOf(record(data = mapOf("value" to -1)), record(data = mapOf("value" to 1.5)),
            record(id = "preference_unknown"), record() + ("role" to "PARENT"),
            record(id = "profile", data = mapOf("name" to "X")))) {
            assertNotNull(AccountRecordPolicy.validate(bad.text("id"), bad))
        }
    }
}
