/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * The role of a cloud (Firebase) account in the Phase B backend.
 *
 * A [PARENT] authenticates with a real email + password; a [CHILD] authenticates
 * with a synthetic email derived from their 12-digit ID plus a 6-digit PIN.
 */
enum class AccountRole {
    INDIVIDUAL,
    PARENT,
    CHILD
}
