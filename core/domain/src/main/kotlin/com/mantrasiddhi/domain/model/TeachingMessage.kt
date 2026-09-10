package com.mantrasiddhi.domain.model

data class TeachingMessage(
    val id: String,
    val revision: Long,
    val title: String,
    val summary: String,
    val body: String,
    val attribution: String,
    val publishedAt: Long,
    val expiresAt: Long,
    val art: String = "river",
    val pinned: Boolean = false
) {
    fun isVisible(now: Long) = publishedAt <= now && now < expiresAt
}

/** These messages have no quiet hours or daily sending limit. */
object TeachingDeliveryPolicy {
    fun canDisplay(enabled: Boolean, practicing: Boolean, expiresAt: Long, now: Long) =
        enabled && !practicing && now < expiresAt
    fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,100}"))
}
