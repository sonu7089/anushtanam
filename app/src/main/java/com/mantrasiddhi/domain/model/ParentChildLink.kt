/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * A parent <-> child link record.
 *
 * @property id The Firestore document id, `"${parentUid}_${childUid}"`.
 */
data class ParentChildLink(
    val id: String,
    val parentUid: String,
    val childUid: String,
    val childName: String,
    val parentName: String,
    val status: LinkStatus,
    val createdAt: Long? = null
)
