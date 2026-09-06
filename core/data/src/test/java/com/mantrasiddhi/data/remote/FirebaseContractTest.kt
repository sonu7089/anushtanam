/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirebaseContractTest {

    @Test
    fun `childEmail builds the synthetic address`() {
        assertEquals("c123456789012@child.anushtanam.app", FirebaseContract.childEmail("123456789012"))
    }

    @Test
    fun `isChildEmail true only for synthetic child domain`() {
        assertTrue(FirebaseContract.isChildEmail("c123456789012@child.anushtanam.app"))
        assertFalse(FirebaseContract.isChildEmail("parent@example.com"))
        assertFalse(FirebaseContract.isChildEmail(null))
    }

    @Test
    fun `codeFromChildEmail round-trips a valid 12-digit code`() {
        val code = "123456789012"
        val email = FirebaseContract.childEmail(code)
        assertEquals(code, FirebaseContract.codeFromChildEmail(email))
    }

    @Test
    fun `codeFromChildEmail rejects non-child and malformed codes`() {
        assertNull(FirebaseContract.codeFromChildEmail("parent@example.com"))
        assertNull(FirebaseContract.codeFromChildEmail("cABC@child.anushtanam.app"))
        assertNull(FirebaseContract.codeFromChildEmail("c123@child.anushtanam.app"))
    }

    @Test
    fun `linkId concatenates parent and child uids`() {
        assertEquals("p1_c2", FirebaseContract.linkId("p1", "c2"))
    }

    @Test
    fun `code and pin length constants match spec`() {
        assertEquals(12, FirebaseContract.CODE_LENGTH)
        assertEquals(6, FirebaseContract.PIN_LENGTH)
    }
}
