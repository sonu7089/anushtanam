/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * The kind of non-ledger reward a parent (or the reciter) attaches to a target.
 *
 * The app does not track a money balance: on completion the reward is simply
 * marked earned, with the app assuming the parent honours their promise. [MONEY]
 * is kept only as a free-form label option (e.g. "₹100 pocket money"), not a
 * virtual-savings amount.
 *
 * Kept dependency-free (no Android imports) so it lives cleanly in the domain layer.
 *
 * @property emoji A short glyph used to decorate the reward in the UI.
 */
enum class RewardType(val emoji: String) {
    MONEY("💰"),
    FOOD("🍽️"),
    GIFT("🎁"),
    OUTING("🎡"),
    TOY("🧸"),
    OTHER("⭐");

    companion object {
        /** Parses [name] back to a [RewardType], falling back to [OTHER] for unknown values. */
        fun fromName(name: String?): RewardType =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}
