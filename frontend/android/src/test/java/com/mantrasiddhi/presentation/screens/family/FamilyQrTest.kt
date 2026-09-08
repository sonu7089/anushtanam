package com.mantrasiddhi.presentation.screens.family

import com.google.zxing.qrcode.decoder.Decoder
import com.mantrasiddhi.domain.model.FamilyQrInvite
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FamilyQrTest {
    @Test fun `encoded QR decodes to exactly the single-use invitation payload`() {
        val invite = FamilyQrInvite("a".repeat(64), 1000)
        val pixels = familyQrMatrix(invite.payload)
        val detector = com.google.zxing.qrcode.detector.Detector(pixels).detect()
        assertEquals(invite.payload, Decoder().decode(detector.bits).text)
    }

    @Test fun `only exact versioned invitation URIs are accepted`() {
        val valid = FamilyQrInvite("f".repeat(64), 1000).payload
        assertEquals("f".repeat(64), FamilyQrInvite.tokenFrom(valid))
        listOf("123456789012", "https://example.com/", valid + "?pin=123456", valid + "/",
            valid.replace("v1", "v2"), valid.replace("f", "F"), " " + valid,
            "anushtanam://family-link/v1/../users/child").forEach {
            assertNull(FamilyQrInvite.tokenFrom(it), it)
        }
    }
}
