package com.mantrasiddhi.data.sync

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BackupFingerprintTest {
    @Test fun fieldOrderAndFirestoreNumberTypesDoNotCreateChanges() {
        val local = linkedMapOf("quantity" to 540, "time" to 1789012800000L, "note" to "om\nshanti", "end" to null)
        val remote = linkedMapOf("end" to null, "note" to "om\nshanti", "time" to 1789012800000L, "quantity" to 540L)
        assertEquals(BackupFingerprint.encode(local), BackupFingerprint.encode(remote))
        assertEquals(BackupFingerprint.encode(local), BackupFingerprint.encode(CloudSyncPolicy.decode(BackupFingerprint.encode(remote))))
        assertNotEquals(BackupFingerprint.encode(local), BackupFingerprint.encode(remote + ("quantity" to 541L)))
        assertNotEquals(BackupFingerprint.encode(local), BackupFingerprint.encode(remote + ("time" to 1789012800001L)))
    }
}
