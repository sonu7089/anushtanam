/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoiceModelSupportTest {

    @Test
    fun `matchesRequired accepts Hindi tags regardless of region and separator`() {
        assertTrue(VoiceModelSupport.matchesRequired("hi-IN"))
        assertTrue(VoiceModelSupport.matchesRequired("hi_IN"))
        assertTrue(VoiceModelSupport.matchesRequired("hi"))
        assertTrue(VoiceModelSupport.matchesRequired("HI-in"))
        assertTrue(VoiceModelSupport.matchesRequired("hi-Latn-IN"))
        assertTrue(VoiceModelSupport.matchesRequired("  hi-IN  "))
    }

    @Test
    fun `matchesRequired rejects non-Hindi and ambiguous tags`() {
        assertFalse(VoiceModelSupport.matchesRequired("en-US"))
        assertFalse(VoiceModelSupport.matchesRequired("en-IN"))
        assertFalse(VoiceModelSupport.matchesRequired(""))
        // Must not over-match a different language whose code merely starts with "hi".
        assertFalse(VoiceModelSupport.matchesRequired("hin"))
    }

    @Test
    fun `resolveState reports READY when the language is installed`() {
        val state = VoiceModelSupport.resolveState(
            installed = listOf("en-US", "hi-IN"),
            pending = emptyList(),
            supported = listOf("hi-IN")
        )
        assertEquals(VoiceModelState.READY, state)
    }

    @Test
    fun `resolveState reports DOWNLOADING when pending but not installed`() {
        val state = VoiceModelSupport.resolveState(
            installed = listOf("en-US"),
            pending = listOf("hi-IN"),
            supported = listOf("hi-IN")
        )
        assertEquals(VoiceModelState.DOWNLOADING, state)
    }

    @Test
    fun `resolveState reports NOT_INSTALLED when only supported`() {
        val state = VoiceModelSupport.resolveState(
            installed = listOf("en-US"),
            pending = emptyList(),
            supported = listOf("en-US", "hi-IN")
        )
        assertEquals(VoiceModelState.NOT_INSTALLED, state)
    }

    @Test
    fun `resolveState reports UNSUPPORTED when Hindi is absent everywhere`() {
        val state = VoiceModelSupport.resolveState(
            installed = listOf("en-US"),
            pending = emptyList(),
            supported = listOf("en-US", "fr-FR")
        )
        assertEquals(VoiceModelState.UNSUPPORTED, state)
    }

    @Test
    fun `resolveState reports UNSUPPORTED for empty support lists`() {
        val state = VoiceModelSupport.resolveState(
            installed = emptyList(),
            pending = emptyList(),
            supported = emptyList()
        )
        assertEquals(VoiceModelState.UNSUPPORTED, state)
    }
}
