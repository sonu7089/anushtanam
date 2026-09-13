/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * A signed-in cloud account.
 *
 * @property uid Firebase Auth uid.
 * @property role PARENT or CHILD.
 * @property displayName Human-readable name.
 * @property linkCode The child's 12-digit ID (children only; null for parents). Legacy metadata only; family linking uses expiring QR tokens.
 * @property email The account email (the Google email for children); may be null when unknown offline.
 */
data class AuthAccount(
    val uid: String,
    val role: AccountRole,
    val displayName: String,
    val linkCode: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val googleLinked: Boolean = false,
    val photoSource: String? = null,
    val profileNeedsSync: Boolean = false
)
