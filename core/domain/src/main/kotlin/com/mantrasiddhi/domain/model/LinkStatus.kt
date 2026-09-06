/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * Lifecycle of a parent -> child link request.
 *
 * A parent creates a [PENDING] link; the child either [APPROVED]s or [REJECTED]s it.
 * Only an [APPROVED] link authorises a parent to set/read that child's goals.
 */
enum class LinkStatus {
    PENDING,
    APPROVED,
    REJECTED;

    companion object {
        fun fromName(name: String?): LinkStatus =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}
